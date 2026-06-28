package com.portal.api;

/**
 * A pluggable LLM backend for the AI Security Analyst. Implementations only need to
 * turn a (system, user) prompt into text; AiAnalyst owns the security-specific prompts
 * and response parsing. Lets the portal use Claude or a free provider (Groq) by key.
 */
public interface LlmProvider {
    boolean isConfigured();
    /** Short human label for the UI, e.g. "Claude (claude-opus-4-8)" or "Groq (llama-3.3-70b)". */
    String name();
    /** Single-shot completion. Throws on transport/auth/HTTP errors. */
    String complete(String system, String user, int maxTokens);
}
