"""
RAG (Retrieval Augmented Generation) Service
Handles vector embeddings, storage, and retrieval for the AI chatbot
"""

import os
import json
import hashlib
from typing import List, Dict, Optional, Any
from dataclasses import dataclass
import asyncio

# ChromaDB for vector storage
import chromadb
from chromadb.config import Settings as ChromaSettings

# OpenAI for embeddings
from openai import OpenAI

# Tiktoken for token counting
import tiktoken


@dataclass
class Document:
    """Represents a document chunk for RAG"""
    id: str
    content: str
    metadata: Dict[str, Any]
    embedding: Optional[List[float]] = None


@dataclass
class RetrievalResult:
    """Result from retrieval query"""
    content: str
    metadata: Dict[str, Any]
    score: float


class TextChunker:
    """Splits text into chunks for embedding"""

    def __init__(self, chunk_size: int = 500, chunk_overlap: int = 50):
        self.chunk_size = chunk_size
        self.chunk_overlap = chunk_overlap
        self.tokenizer = tiktoken.get_encoding("cl100k_base")

    def count_tokens(self, text: str) -> int:
        """Count tokens in text"""
        return len(self.tokenizer.encode(text))

    def chunk_text(self, text: str, metadata: Dict[str, Any] = None) -> List[Document]:
        """Split text into chunks with overlap"""
        if metadata is None:
            metadata = {}

        # Split by paragraphs first
        paragraphs = text.split('\n\n')
        chunks = []
        current_chunk = ""
        current_tokens = 0

        for para in paragraphs:
            para = para.strip()
            if not para:
                continue

            para_tokens = self.count_tokens(para)

            # If single paragraph exceeds chunk size, split by sentences
            if para_tokens > self.chunk_size:
                if current_chunk:
                    chunks.append(current_chunk.strip())
                    current_chunk = ""
                    current_tokens = 0

                # Split large paragraph by sentences
                sentences = self._split_sentences(para)
                for sentence in sentences:
                    sent_tokens = self.count_tokens(sentence)
                    if current_tokens + sent_tokens > self.chunk_size and current_chunk:
                        chunks.append(current_chunk.strip())
                        # Keep overlap
                        overlap_text = self._get_overlap(current_chunk)
                        current_chunk = overlap_text + " " + sentence
                        current_tokens = self.count_tokens(current_chunk)
                    else:
                        current_chunk += " " + sentence if current_chunk else sentence
                        current_tokens += sent_tokens
            else:
                # Add paragraph to current chunk
                if current_tokens + para_tokens > self.chunk_size and current_chunk:
                    chunks.append(current_chunk.strip())
                    # Keep overlap
                    overlap_text = self._get_overlap(current_chunk)
                    current_chunk = overlap_text + "\n\n" + para
                    current_tokens = self.count_tokens(current_chunk)
                else:
                    current_chunk += "\n\n" + para if current_chunk else para
                    current_tokens += para_tokens

        # Add remaining chunk
        if current_chunk.strip():
            chunks.append(current_chunk.strip())

        # Convert to Document objects
        documents = []
        for i, chunk in enumerate(chunks):
            doc_id = hashlib.md5(f"{chunk[:100]}_{i}".encode()).hexdigest()
            documents.append(Document(
                id=doc_id,
                content=chunk,
                metadata={**metadata, "chunk_index": i, "total_chunks": len(chunks)}
            ))

        return documents

    def _split_sentences(self, text: str) -> List[str]:
        """Split text into sentences"""
        import re
        # Simple sentence splitting
        sentences = re.split(r'(?<=[.!?])\s+', text)
        return [s.strip() for s in sentences if s.strip()]

    def _get_overlap(self, text: str) -> str:
        """Get the last portion of text for overlap"""
        tokens = self.tokenizer.encode(text)
        if len(tokens) <= self.chunk_overlap:
            return text
        overlap_tokens = tokens[-self.chunk_overlap:]
        return self.tokenizer.decode(overlap_tokens)


class EmbeddingService:
    """Generates embeddings using OpenAI"""

    def __init__(self, api_key: str = None, model: str = "text-embedding-3-small"):
        self.api_key = api_key
        self.model = model
        self._client = None

    def configure(self, api_key: str):
        """Configure the embedding service"""
        self.api_key = api_key
        self._client = None

    @property
    def client(self) -> OpenAI:
        if self._client is None:
            if not self.api_key:
                raise ValueError("OpenAI API key not configured")
            self._client = OpenAI(api_key=self.api_key)
        return self._client

    def is_configured(self) -> bool:
        return self.api_key is not None

    def get_embedding(self, text: str) -> List[float]:
        """Get embedding for a single text"""
        response = self.client.embeddings.create(
            model=self.model,
            input=text
        )
        return response.data[0].embedding

    def get_embeddings_batch(self, texts: List[str]) -> List[List[float]]:
        """Get embeddings for multiple texts"""
        response = self.client.embeddings.create(
            model=self.model,
            input=texts
        )
        return [item.embedding for item in response.data]


class VectorStore:
    """ChromaDB-based vector store for document storage and retrieval"""

    def __init__(self, persist_directory: str = None, collection_name: str = "optix_knowledge"):
        self.persist_directory = persist_directory or os.path.join(
            os.path.dirname(__file__), "..", "..", "data", "chroma_db"
        )
        self.collection_name = collection_name
        self._client = None
        self._collection = None

    @property
    def client(self) -> chromadb.Client:
        if self._client is None:
            # Ensure directory exists
            os.makedirs(self.persist_directory, exist_ok=True)

            self._client = chromadb.PersistentClient(
                path=self.persist_directory,
                settings=ChromaSettings(
                    anonymized_telemetry=False,
                    allow_reset=True
                )
            )
        return self._client

    @property
    def collection(self) -> chromadb.Collection:
        if self._collection is None:
            self._collection = self.client.get_or_create_collection(
                name=self.collection_name,
                metadata={"hnsw:space": "cosine"}  # Use cosine similarity
            )
        return self._collection

    def add_documents(self, documents: List[Document], embeddings: List[List[float]]):
        """Add documents with their embeddings to the store"""
        ids = [doc.id for doc in documents]
        contents = [doc.content for doc in documents]
        metadatas = [doc.metadata for doc in documents]

        self.collection.add(
            ids=ids,
            embeddings=embeddings,
            documents=contents,
            metadatas=metadatas
        )

    def query(
        self,
        query_embedding: List[float],
        n_results: int = 5,
        where: Dict = None,
        where_document: Dict = None
    ) -> List[RetrievalResult]:
        """Query the vector store for similar documents"""
        results = self.collection.query(
            query_embeddings=[query_embedding],
            n_results=n_results,
            where=where,
            where_document=where_document,
            include=["documents", "metadatas", "distances"]
        )

        retrieval_results = []
        if results and results['documents'] and results['documents'][0]:
            for i, doc in enumerate(results['documents'][0]):
                # Convert distance to similarity score (cosine distance -> similarity)
                distance = results['distances'][0][i] if results['distances'] else 0
                score = 1 - distance  # Cosine similarity = 1 - cosine distance

                retrieval_results.append(RetrievalResult(
                    content=doc,
                    metadata=results['metadatas'][0][i] if results['metadatas'] else {},
                    score=score
                ))

        return retrieval_results

    def delete_collection(self):
        """Delete the entire collection"""
        try:
            self.client.delete_collection(self.collection_name)
            self._collection = None
        except Exception:
            pass

    def get_stats(self) -> Dict:
        """Get collection statistics"""
        return {
            "name": self.collection_name,
            "count": self.collection.count(),
            "persist_directory": self.persist_directory
        }


class RAGService:
    """
    Main RAG service that orchestrates chunking, embedding, storage, and retrieval
    """

    def __init__(self):
        self.chunker = TextChunker(chunk_size=400, chunk_overlap=50)
        self.embedding_service = EmbeddingService()
        self.vector_store = VectorStore()
        self._is_initialized = False

    def configure(self, openai_api_key: str):
        """Configure the RAG service with API key"""
        self.embedding_service.configure(openai_api_key)

    def is_configured(self) -> bool:
        return self.embedding_service.is_configured()

    def is_initialized(self) -> bool:
        """Check if knowledge base has been loaded"""
        return self._is_initialized and self.vector_store.collection.count() > 0

    def add_document(self, content: str, metadata: Dict[str, Any] = None) -> int:
        """Add a single document to the knowledge base"""
        if not self.is_configured():
            raise ValueError("RAG service not configured. Set OpenAI API key first.")

        # Chunk the document
        documents = self.chunker.chunk_text(content, metadata)

        if not documents:
            return 0

        # Generate embeddings
        texts = [doc.content for doc in documents]
        embeddings = self.embedding_service.get_embeddings_batch(texts)

        # Store in vector database
        self.vector_store.add_documents(documents, embeddings)

        return len(documents)

    def add_documents_batch(self, documents_data: List[Dict[str, Any]]) -> int:
        """
        Add multiple documents to the knowledge base

        Args:
            documents_data: List of dicts with 'content' and optional 'metadata'
        """
        if not self.is_configured():
            raise ValueError("RAG service not configured. Set OpenAI API key first.")

        all_documents = []

        for doc_data in documents_data:
            content = doc_data.get('content', '')
            metadata = doc_data.get('metadata', {})

            if content:
                chunks = self.chunker.chunk_text(content, metadata)
                all_documents.extend(chunks)

        if not all_documents:
            return 0

        # Generate embeddings in batches (OpenAI limit is ~8000 per request)
        batch_size = 100
        for i in range(0, len(all_documents), batch_size):
            batch = all_documents[i:i + batch_size]
            texts = [doc.content for doc in batch]
            embeddings = self.embedding_service.get_embeddings_batch(texts)
            self.vector_store.add_documents(batch, embeddings)

        self._is_initialized = True
        return len(all_documents)

    def retrieve(
        self,
        query: str,
        n_results: int = 5,
        min_score: float = 0.3
    ) -> List[RetrievalResult]:
        """
        Retrieve relevant documents for a query

        Args:
            query: User's question
            n_results: Number of results to return
            min_score: Minimum similarity score (0-1)
        """
        if not self.is_configured():
            return []

        # Generate query embedding
        query_embedding = self.embedding_service.get_embedding(query)

        # Query vector store
        results = self.vector_store.query(query_embedding, n_results=n_results)

        # Filter by minimum score
        results = [r for r in results if r.score >= min_score]

        return results

    def get_context_for_query(
        self,
        query: str,
        max_tokens: int = 2000,
        n_results: int = 5
    ) -> str:
        """
        Get formatted context string for a query to inject into LLM prompt

        Args:
            query: User's question
            max_tokens: Maximum tokens for context
            n_results: Number of documents to retrieve
        """
        results = self.retrieve(query, n_results=n_results)

        if not results:
            return ""

        context_parts = []
        total_tokens = 0

        for result in results:
            # Count tokens
            tokens = self.chunker.count_tokens(result.content)

            if total_tokens + tokens > max_tokens:
                break

            # Format with metadata
            category = result.metadata.get('category', 'General')
            topic = result.metadata.get('topic', '')

            if topic:
                context_parts.append(f"[{category} - {topic}]\n{result.content}")
            else:
                context_parts.append(f"[{category}]\n{result.content}")

            total_tokens += tokens

        return "\n\n---\n\n".join(context_parts)

    def reset_knowledge_base(self):
        """Clear all documents from the knowledge base"""
        self.vector_store.delete_collection()
        self._is_initialized = False

    def get_stats(self) -> Dict:
        """Get RAG service statistics"""
        return {
            "configured": self.is_configured(),
            "initialized": self.is_initialized(),
            "vector_store": self.vector_store.get_stats()
        }


# Global singleton instance
rag_service = RAGService()
