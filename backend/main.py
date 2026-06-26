"""
PayAssist AI Backend
FastAPI Server with RAG + ChromaDB + Ollama

"""

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import List, Optional
import chromadb
from sentence_transformers import SentenceTransformer
import uvicorn
import requests
import re

# Initialize FastAPI app
app = FastAPI(
    title="PayAssist AI Backend",
    description="AI-powered payment support chatbot with RAG",
    version="2.4.2"
)

# Enable CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# -------------------- RAG SETUP --------------------
print("🚀 Loading RAG components...")
CHROMA_PATH = "./chroma_db"
COLLECTION_NAME = "payment_docs"

try:
    embedding_model = SentenceTransformer('all-MiniLM-L6-v2')
    client = chromadb.PersistentClient(path=CHROMA_PATH)
    collection = client.get_collection(name=COLLECTION_NAME)
    print(f"✅ ChromaDB collection loaded with {collection.count()} documents.")
except Exception as e:
    print(f"⚠️ Error: {e}")
    collection = None

# -------------------- OLLAMA SETUP --------------------
OLLAMA_URL = "http://localhost:11434/api/generate"
MODEL_NAME = "llama3.2"

def check_ollama_available() -> bool:
    try:
        response = requests.get("http://localhost:11434/api/tags", timeout=2)
        if response.status_code == 200:
            models = response.json().get("models", [])
            return any(m.get("name", "").startswith(MODEL_NAME) for m in models)
    except:
        pass
    return False

def generate_with_ollama(prompt: str) -> str:
    payload = {
        "model": MODEL_NAME,
        "prompt": prompt,
        "stream": False,
        "temperature": 0.3,
        "max_tokens": 600
    }
    try:
        response = requests.post(OLLAMA_URL, json=payload, timeout=60)
        response.raise_for_status()
        answer = response.json().get("response")
        if not answer or not answer.strip():
            print("❌ Ollama returned an empty response.")
            return None
        return answer
    except Exception as e:
        print(f"❌ Ollama error: {e}")
        return None

OLLAMA_AVAILABLE = check_ollama_available()
if OLLAMA_AVAILABLE:
    print(f"✅ Ollama is running with model: {MODEL_NAME}")
else:
    print("⚠️ Ollama is not running or model not available.")

# -------------------- MODELS --------------------
class QuestionRequest(BaseModel):
    question: str
    transaction_id: Optional[str] = None

class QuestionResponse(BaseModel):
    answer: str
    sources: List[str] = []
    relevance: Optional[float] = None
    error: Optional[str] = None

class TransactionRequest(BaseModel):
    transaction_id: str

class TransactionResponse(BaseModel):
    status: str
    amount: float
    merchant: str
    decline_code: Optional[str] = None
    decline_reason: Optional[str] = None

class PaymentRequest(BaseModel):
    card_number: str
    pin: str
    amount: float
    merchant: str

class PaymentResponse(BaseModel):
    status: str
    amount: float
    merchant: str
    decline_reason: Optional[str] = None
    new_balance: Optional[float] = None
    daily_remaining: Optional[float] = None
    monthly_remaining: Optional[float] = None

# -------------------- MOCK DATA --------------------
MOCK_TRANSACTIONS = {
    "TXN12345": {"status": "Approved", "amount": 99.99, "merchant": "Carrefour", "decline_code": None, "decline_reason": None},
    "TXN67890": {"status": "Declined", "amount": 150.00, "merchant": "Monoprix", "decline_code": "51", "decline_reason": "Insufficient funds"},
    "TXN11111": {"status": "Approved", "amount": 45.00, "merchant": "Orange Store", "decline_code": None, "decline_reason": None},
    "TXN22222": {"status": "Declined", "amount": 250.00, "merchant": "Tunisiana", "decline_code": "05", "decline_reason": "Do not honor - Contact bank"},
    "TXN33333": {"status": "Approved", "amount": 9.99, "merchant": "Coffee Shop", "decline_code": None, "decline_reason": None}
}

# Mock card balance
card_balance = 1000.0
daily_spent = 0.0
monthly_spent = 0.0
DAILY_LIMIT = 500.0
MONTHLY_LIMIT = 2000.0

# Matches error/decline codes shaped like "DEC-001", "SEC-002", "TRM-005",
# "PIN-001", or spaced/loosely punctuated variants like "DEC 001" — generic
# across any guide that uses a PREFIX-NUMBER code scheme, rather than one
# hardcoded list of prefixes.
CODE_PATTERN = re.compile(r"\b([A-Z]{2,6})[-\s]?(\d{2,4})\b")

# -------------------- HELPERS --------------------
def is_greeting(text: str) -> bool:
    greetings = ["hello", "hi", "hey", "good morning", "good afternoon", "good evening", "howdy", "yo"]
    cleaned = text.lower().strip().strip("!.,?")
    return cleaned in greetings

def calculate_relevance(answer: str, chunks: List[str], question: str = "") -> float:
    low_phrases = [
        "i'm not able", "i don't know", "i cannot answer", "not related",
        "i'm sorry", "i don't have enough information", "cannot provide",
        "unable to answer", "i don't have that information"
    ]
    answer_lower = answer.lower()
    for phrase in low_phrases:
        if phrase in answer_lower:
            return 20

    if len(answer.strip()) < 30:
        return 10

    chunk_score = min(len(chunks), 5) * 15
    bonus = 10 if chunks else 0

    keyword_bonus = 0
    if question:
        words = question.split()
        for word in words:
            clean_word = word.strip("?.!,;:").upper()
            if len(clean_word) >= 2 and clean_word in answer_lower.upper():
                keyword_bonus = 15
                break

    return min(85, chunk_score + bonus + keyword_bonus)

def _find_code_in_text(text: str) -> Optional[str]:
    """Return the first PREFIX-NUMBER style code found in text, normalized as PREFIX-NUMBER."""
    match = CODE_PATTERN.search(text.upper())
    if not match:
        return None
    return f"{match.group(1)}-{match.group(2)}"

def get_chunk_by_error_code(code: str, n: int = 3):
    """
    Direct metadata lookup: if the question names a specific error code
    (e.g. "DEC-001"), fetch that chunk from ChromaDB by its error_code
    metadata field instead of relying on vector similarity to rank it
    first. This is exact and doesn't depend on embedding quality at all.

    Returns (documents, metadatas) matching ChromaDB's usual result
    shape, or (None, None) if nothing matched.
    """
    if collection is None:
        return None, None
    try:
        result = collection.get(where={"error_code": code}, limit=n)
        docs = result.get("documents") or []
        metas = result.get("metadatas") or []
        if not docs:
            return None, None
        return docs, metas
    except Exception as e:
        print(f"⚠️ Metadata lookup error: {e}")
        return None, None

def extract_relevant_answer(context: str, question: str) -> str:
    """
    Generic, document-agnostic fallback used when the LLM is unavailable
    (or returns an error). It does not hardcode specific terms from one
    guide — instead:

    1. If the question mentions an error/decline code (e.g. "DEC-001",
       "SEC-002", "what does code 51 mean"), look for that same code
       inside the retrieved context and return the surrounding paragraph.
    2. If the question contains a bare "code <number>", search for that.
    3. If the question mentions "insufficient funds" or "declined" but no
       code was found, look for a relevant paragraph containing those terms.
    4. Otherwise, just return the top retrieved chunk, lightly trimmed.
    """
    # 1. Check for PREFIX-NUMBER style code in the question
    question_code = _find_code_in_text(question)
    if question_code:
        prefix, number = question_code.split("-")
        # Look for the same code (allowing loose punctuation/spacing) inside the context.
        pattern = re.compile(
            rf"{prefix}[-\s]?{number}[\s\S]{{0,600}}?(?=\n[A-Z]{{2,6}}[-\s]?\d{{2,4}}\b|\Z)",
            re.IGNORECASE
        )
        match = pattern.search(context)
        if match:
            return f"📚 Based on the documentation:\n\n{match.group(0).strip()}"

    # 2. Try bare number (e.g. "code 51") against a "code <num>" mention.
    number_match = re.search(r"\bcode\s*(\d{2,4})\b", question.lower())
    if number_match:
        number = number_match.group(1)
        pattern = re.compile(rf"\b{number}\b[\s\S]{{0,400}}", re.IGNORECASE)
        match = pattern.search(context)
        if match:
            return f"📚 Based on the documentation:\n\n{match.group(0).strip()}"

    # 3. If the question mentions "insufficient funds" or "declined" but no
    #    code matched above, look for a paragraph containing those terms.
    #    NOTE: uses [\s\S] instead of a bare "." — "." does not match
    #    newlines in Python by default, and `context` is chunks joined with
    #    "\n\n", so a bare "." here would silently fail to match whenever
    #    the keyword sits near a line break (the common case). [\s\S] (or
    #    re.DOTALL) is required for this to reliably match multi-line context.
    if "insufficient funds" in question.lower() or "declined" in question.lower():
        pattern = re.compile(r"[\s\S]{0,200}(insufficient|declined)[\s\S]{0,200}", re.IGNORECASE)
        match = pattern.search(context)
        if match:
            return f"📚 Based on the documentation:\n\n{match.group(0).strip()}"

    # 4. Generic fallback: no specific code found — just surface the top chunk.
    trimmed = context.strip()
    if len(trimmed) > 500:
        trimmed = trimmed[:500].rsplit(" ", 1)[0] + "..."
    return f"📚 Based on the documentation:\n\n{trimmed}"

# -------------------- ENDPOINTS --------------------
@app.get("/")
async def root():
    return {
        "message": "🚀 PayAssist AI Backend is running!",
        "version": "2.4.2",
        "model": MODEL_NAME if OLLAMA_AVAILABLE else "None (fallback)",
        "endpoints": [
            "GET  / - Root",
            "GET  /health - Health check",
            "POST /ask - Ask a question",
            "POST /transactions - Get transaction status",
            "POST /process_payment - Process a payment"
        ]
    }

@app.get("/health")
async def health_check():
    return {"status": "healthy", "service": "PayAssist AI"}

@app.post("/ask", response_model=QuestionResponse)
async def ask_question(request: QuestionRequest):
    try:
        if is_greeting(request.question):
            return QuestionResponse(
                answer="Hello! How can I assist you with payment processing today? Ask me about a specific error code, a decline reason, or any payment-related topic covered in the guides.",
                sources=[],
                relevance=0
            )

        if collection is None:
            return QuestionResponse(
                answer="⚠️ RAG system not initialized. Please run ingest.py first.",
                sources=[],
                relevance=0
            )

        # ---- Try an exact error-code match first ----
        # If the question names a specific code (e.g. "DEC-001", "what is
        # TRM 005"), fetch that chunk directly via metadata instead of
        # hoping vector similarity ranks it first. This is exact and
        # doesn't depend on embedding quality.
        question_code = _find_code_in_text(request.question)
        exact_docs, exact_metas = (None, None)
        if question_code:
            exact_docs, exact_metas = get_chunk_by_error_code(question_code)

        question_embedding = embedding_model.encode(request.question).tolist()
        vector_results = collection.query(query_embeddings=[question_embedding], n_results=5)

        vector_docs = vector_results['documents'][0] if vector_results.get('documents') else []
        vector_metas = vector_results['metadatas'][0] if vector_results.get('metadatas') else []

        if exact_docs:
            # Put the exact-match chunk first, then fill in with the top
            # vector-search results (skipping anything already included)
            # for extra surrounding context.
            combined_docs = list(exact_docs)
            combined_metas = list(exact_metas)
            for doc, meta in zip(vector_docs, vector_metas):
                if doc not in combined_docs:
                    combined_docs.append(doc)
                    combined_metas.append(meta)
        else:
            combined_docs = vector_docs
            combined_metas = vector_metas

        if not combined_docs:
            fallback_answer = "I couldn't find any information about that in the payment documents. Please ask a specific payment-related question."
            return QuestionResponse(
                answer=fallback_answer,
                sources=[],
                relevance=10
            )

        chunks = combined_docs[:3]
        sources = combined_metas[:3]
        context = "\n\n".join(chunks)

        prompt = f"""You are PayAssist, a payment support assistant.

Your task is to answer the user's question using ONLY the context provided below.
The context comes from a payment documentation guide.

Extract the most relevant information from the context and provide a clear, concise answer.
If the context does NOT contain the answer, say exactly: "I don't have that information in my knowledge base."
Do NOT make up information or use external knowledge.
Keep your answer concise (2-3 sentences maximum) unless the question requires more detail.

CONTEXT:
{context}

USER QUESTION: {request.question}

YOUR ANSWER (be concise):"""

        if OLLAMA_AVAILABLE:
            answer = generate_with_ollama(prompt)
            if answer is None:
                answer = extract_relevant_answer(context, request.question)
        else:
            answer = extract_relevant_answer(context, request.question)

        # If the model came back too thin or explicitly said it didn't know,
        # fall back to the generic extractor rather than a document-specific hack.
        if len(answer.strip()) < 20 or "I don't have that information" in answer:
            answer = extract_relevant_answer(context, request.question)

        relevance = calculate_relevance(answer, chunks, request.question)
        if exact_docs:
            # We found the exact chunk by metadata, not by similarity guesswork.
            relevance = max(relevance, 90)

        source_strings = [f"{s.get('source', 'Unknown')} - {s.get('error_code') or s.get('page', '')}" for s in sources]

        return QuestionResponse(
            answer=answer,
            sources=source_strings,
            relevance=relevance
        )

    except Exception as e:
        return QuestionResponse(
            answer=f"I'm sorry, an error occurred. Please try again.",
            sources=[],
            relevance=0,
            error=str(e)
        )

@app.post("/transactions")
async def get_transaction(request: TransactionRequest):
    transaction_id = request.transaction_id.upper()
    if transaction_id in MOCK_TRANSACTIONS:
        data = MOCK_TRANSACTIONS[transaction_id]
        return TransactionResponse(**data)
    else:
        return TransactionResponse(status="Not Found", amount=0.0, merchant="Unknown", decline_code=None, decline_reason="Transaction ID not found")

@app.post("/process_payment")
async def process_payment(request: PaymentRequest):
    global card_balance, daily_spent, monthly_spent

    # Validate PIN
    if request.pin != "1234":
        return PaymentResponse(
            status="Declined",
            amount=request.amount,
            merchant=request.merchant,
            decline_reason="Incorrect PIN"
        )

    # Check card balance
    if request.amount > card_balance:
        return PaymentResponse(
            status="Declined",
            amount=request.amount,
            merchant=request.merchant,
            decline_reason="Insufficient funds"
        )

    # Check daily limit
    if daily_spent + request.amount > DAILY_LIMIT:
        return PaymentResponse(
            status="Declined",
            amount=request.amount,
            merchant=request.merchant,
            decline_reason="Exceeds daily limit"
        )

    # Check monthly limit
    if monthly_spent + request.amount > MONTHLY_LIMIT:
        return PaymentResponse(
            status="Declined",
            amount=request.amount,
            merchant=request.merchant,
            decline_reason="Exceeds monthly limit"
        )

    # Approve transaction
    card_balance -= request.amount
    daily_spent += request.amount
    monthly_spent += request.amount

    return PaymentResponse(
        status="Approved",
        amount=request.amount,
        merchant=request.merchant,
        decline_reason=None,
        new_balance=card_balance,
        daily_remaining=DAILY_LIMIT - daily_spent,
        monthly_remaining=MONTHLY_LIMIT - monthly_spent
    )

if __name__ == "__main__":
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True)