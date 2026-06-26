"""
PayAssist AI - Document Ingestion Script
Indexes multiple payment documents (DOCX + PDF) into ChromaDB for RAG
"""

import os
import re
import shutil
from langchain_community.document_loaders import Docx2txtLoader, PyPDFLoader
from langchain_text_splitters import RecursiveCharacterTextSplitter
from sentence_transformers import SentenceTransformer
import chromadb

# -------------------- CONFIGURATION --------------------
DOCX_PATH = "PaymentGuide.docx"                     # Technical guide (EMV, ARQC, ISO 8583) - clean prose
PDF_PATH = "Apex_Merchant_Payment_Error_Guide.pdf"  # Merchant error guide (DEC-001, TRM-001, etc.) - structured entries
CHROMA_PATH = "./chroma_db"
COLLECTION_NAME = "payment_docs"
CHUNK_SIZE = 600
CHUNK_OVERLAP = 80

# Matches the "Error Code:" label followed by a code like "DEC-001",
# "PIN-002", "TRM-005" (allowing a line break and optional space/dash
# between the letters and the digits, since PDF text extraction is
# inconsistent about exact spacing).
ERROR_CODE_PATTERN = re.compile(
    r"Error Code:\s*\n?\s*([A-Z]{2,6}[-\s]?\d{2,4})",
    re.IGNORECASE
)

# The header/footer repeated on every single page of the PDF. This adds
# no information and dilutes every chunk's embedding with identical
# boilerplate, so it's stripped before chunking.
HEADER_FOOTER_PATTERN = re.compile(
    r"APEX PAYMENT GUIDE[\s\S]{0,150}?Page\s*\d+",
    re.IGNORECASE
)


def clean_pdf_text(text: str) -> str:
    """Remove the repeated per-page header/footer boilerplate."""
    cleaned = HEADER_FOOTER_PATTERN.sub("", text)
    # Collapse the extra blank lines left behind by the removal
    cleaned = re.sub(r"\n{3,}", "\n\n", cleaned)
    return cleaned.strip()


def split_error_guide(text: str):
    """
    Split the merchant error-guide PDF into one chunk per error-code
    entry (DEC-001, PIN-002, TRM-005, ...) instead of a fixed character
    count. Each entry in this guide is a complete, self-contained unit
    (code, title, severity, "what it means," customer script, numbered
    merchant steps) - splitting on character count risks cutting an
    entry in half, which is the main thing hurting retrieval quality
    for this document.

    Returns a list of (text, error_code) tuples for matched entries.
    Any text before the first match or after the last match (intro
    pages, the quick-reference table, "when to call," communication
    scripts) is returned separately as leftover text to be chunked
    normally.
    """
    matches = list(ERROR_CODE_PATTERN.finditer(text))
    if not matches:
        return [], text  # not this kind of document - caller falls back to generic splitting

    entries = []
    for i, match in enumerate(matches):
        start = match.start()
        end = matches[i + 1].start() if i + 1 < len(matches) else len(text)
        code = match.group(1).upper().replace(" ", "-")
        entry_text = text[start:end].strip()
        entries.append((entry_text, code))

    leftover = text[:matches[0].start()] + "\n\n" + text[matches[-1].end():]
    return entries, leftover


# -------------------- LOAD & CHUNK --------------------
print("📄 Loading documents...")
chunk_texts = []      # list of str
chunk_metadatas = []  # list of dict, same length/order as chunk_texts

text_splitter = RecursiveCharacterTextSplitter(
    chunk_size=CHUNK_SIZE,
    chunk_overlap=CHUNK_OVERLAP,
    separators=["\n\n", "\n", ". ", " ", ""]
)

# 1. DOCX (clean prose - generic splitting works well here)
if os.path.exists(DOCX_PATH):
    try:
        docx_docs = Docx2txtLoader(DOCX_PATH).load()
        docx_chunks = text_splitter.split_documents(docx_docs)
        for chunk in docx_chunks:
            chunk_texts.append(chunk.page_content)
            chunk_metadatas.append({
                "source": DOCX_PATH,
                "page": chunk.metadata.get("page", 0)
            })
        print(f"✅ '{DOCX_PATH}': {len(docx_chunks)} chunks (generic splitter)")
    except Exception as e:
        print(f"⚠️ Error loading DOCX: {e}")
else:
    print(f"⚠️ Warning: '{DOCX_PATH}' not found. Skipping.")

# 2. PDF (structured error entries - split per error code)
if os.path.exists(PDF_PATH):
    try:
        pdf_pages = PyPDFLoader(PDF_PATH).load()
        full_text = clean_pdf_text("\n".join(p.page_content for p in pdf_pages))

        error_entries, leftover_text = split_error_guide(full_text)

        for entry_text, code in error_entries:
            chunk_texts.append(entry_text)
            chunk_metadatas.append({
                "source": PDF_PATH,
                "error_code": code
            })
        print(f"✅ '{PDF_PATH}': {len(error_entries)} error-code chunks (one per entry)")

        # Remaining narrative sections (intro, quick-reference table,
        # "when to call your bank", customer scripts) - chunk normally.
        if leftover_text.strip():
            leftover_chunks = text_splitter.split_text(leftover_text)
            for chunk_text in leftover_chunks:
                if chunk_text.strip():
                    chunk_texts.append(chunk_text)
                    chunk_metadatas.append({"source": PDF_PATH, "error_code": None})
            print(f"✅ '{PDF_PATH}': {len(leftover_chunks)} additional chunks (narrative sections)")
    except Exception as e:
        print(f"⚠️ Error loading PDF: {e}")
else:
    print(f"⚠️ Warning: '{PDF_PATH}' not found. Skipping.")

if not chunk_texts:
    raise FileNotFoundError("No documents found. Please ensure both files are in the backend/ directory.")

print(f"✅ Total chunks created: {len(chunk_texts)}")

# -------------------- EMBEDDINGS --------------------
print("🧠 Generating embeddings...")
model = SentenceTransformer('all-MiniLM-L6-v2')
embeddings = model.encode(chunk_texts, convert_to_numpy=True)
print(f"✅ Generated {len(embeddings)} embeddings.")

# -------------------- STORE IN CHROMADB --------------------
print("💾 Storing in ChromaDB...")
if os.path.exists(CHROMA_PATH):
    shutil.rmtree(CHROMA_PATH)
    print("🗑️  Removed old ChromaDB.")

client = chromadb.PersistentClient(path=CHROMA_PATH)
collection = client.get_or_create_collection(name=COLLECTION_NAME)

for i, (text, embedding, metadata) in enumerate(zip(chunk_texts, embeddings, chunk_metadatas)):
    meta = {"chunk_index": i, **{k: v for k, v in metadata.items() if v is not None}}
    collection.add(
        ids=[f"doc_{i}"],
        documents=[text],
        embeddings=[embedding.tolist()],
        metadatas=[meta]
    )

print(f"✅ Indexed {len(chunk_texts)} chunks into ChromaDB.")
print("🎉 Ingestion complete! You can now restart your backend.")