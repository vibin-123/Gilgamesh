# Gilgamesh — Java NLP Chatbot

A Java-based chatbot that demonstrates **advanced Natural Language Processing (NLP) concepts** without calling any external APIs. Originally built with basic tokenization and Jaccard similarity, Gilgamesh has been upgraded with a powerful hybrid classifier, conversation context memory, anaphora resolution, and a full text-preprocessing pipeline.

---

## 🏗 Architecture

```
User Input (raw text)
      │
      ▼
┌───────────────────────────────────────────────┐
│              ReferenceResolver                │  ← Anaphora Resolution ("it", "he", "she")
│    (Uses active ConversationContext memory)   │
└─────────────────────┬─────────────────────────┘
                      ▼
┌───────────────────────────────────────────────┐
│              TextPreprocessor                 │
│  1. Tokenization (OpenNLP SimpleTokenizer)    │
│  2. Lowercasing & Punctuation Removal         │
│  3. Spell Correction (Levenshtein Distance)   │
│  4. Stopword Removal                          │
│  5. Synonym Normalization                     │
│  6. Stemming (Porter Stemmer)                 │
└─────────────────────┬─────────────────────────┘
                      ▼
┌───────────────────────────────────────────────┐     ┌──────────────────┐
│              IntentClassifier                 │◄────│ IntentRepository │
│  - Jaccard Similarity (Token Overlap)         │     │ (intents.json)   │
│  - TF-IDF & Cosine Similarity                 │     └──────────────────┘
│  - Context-Aware Boosting                     │
└─────────────────────┬─────────────────────────┘
                      ▼
┌───────────────────────────────────────────────┐
│               ResponseEngine                  │
│  - Stateful Template Processor (Variations)   │
│  - Dynamic Variables ({time}, {date}, {topic})│
│  - Progressive Smart Fallbacks                │
└─────────────────────┬─────────────────────────┘
                      ▼
┌───────────────────────────────────────────────┐
│             ConversationContext               │  ← Updates short-term memory
└─────────────────────┬─────────────────────────┘
                      ▼
                 Bot Response
```

---

## 🧠 Advanced NLP Concepts Demonstrated

### 1. Tokenization & Normalization
Splitting raw text into individual tokens using **OpenNLP's `SimpleTokenizer`**. All text is lowercased and stripped of punctuation for consistent processing.

### 2. Stemming
Using a custom pure-Java **Porter Stemmer**, we reduce inflected words to their root forms (e.g., `"running"`, `"runs"`, `"ran"` all become `"run"`), allowing our classifier to match intents regardless of grammatical tense.

### 3. Synonym Matching
A `SynonymProvider` normalizes token variations to canonical forms. For example, `"creator"`, `"developer"`, and `"author"` are all mapped to `"creator"`.

### 4. Fuzzy Spell Correction
A `SpellChecker` uses the **Levenshtein Edit Distance** algorithm to correct user typos against the chatbot's domain vocabulary (e.g., `"wether"` → `"weather"`).

### 5. TF-IDF & Cosine Similarity
In addition to basic Jaccard (bag-of-words) similarity, Gilgamesh uses a **Term Frequency - Inverse Document Frequency (TF-IDF)** vectorizer. This ensures that rare, highly indicative words carry more weight in the classification score than common words.

### 6. Anaphora / Reference Resolution
The chatbot maintains a sliding window of conversation history in `ConversationContext`. The `ReferenceResolver` resolves ambiguous pronouns based on recent topics (e.g., if the topic is `"Gilgamesh"`, the input `"Who created it?"` is resolved to `"Who created Gilgamesh?"`).

### 7. Context-Aware Classification
Intents that align with the current active conversation topic receive a confidence score boost, allowing the chatbot to gracefully handle contextual follow-up questions.

### 8. Progressive Fallbacks & Dynamic Responses
The `ResponseEngine` prevents repeating the exact same response consecutively and substitutes dynamic variables like `{time}` and `{topic}`. If the bot fails to understand the user multiple times, the `FallbackHandler` provides escalating, helpful suggestions rather than repeating the same error message.

---

## 📁 Project Structure

```
Gilgamesh/
├── pom.xml                                    # Maven build config
├── README.md                                  # This file
└── src/
    └── main/
        ├── java/com/gilgamesh/
        │   ├── Main.java                      # Entry point (chat loop)
        │   ├── model/
        │   │   └── Intent.java                # Intent data model
        │   ├── memory/
        │   │   └── ConversationContext.java   # Short-term memory state
        │   ├── nlp/
        │   │   ├── TextPreprocessor.java      # NLP Pipeline 
        │   │   ├── PorterStemmer.java         # Stemming
        │   │   ├── SynonymProvider.java       # Synonym normalization
        │   │   ├── SpellChecker.java          # Edit distance spell checking
        │   │   ├── TfidfVectorizer.java       # TF-IDF & Cosine Similarity
        │   │   ├── ReferenceResolver.java     # Anaphora resolution
        │   │   └── IntentClassifier.java      # Hybrid classification engine
        │   ├── data/
        │   │   └── IntentRepository.java      # JSON data loader
        │   └── response/
        │       ├── ResponseEngine.java        # Response orchestrator
        │       ├── ResponseTemplateProcessor.java # Dynamic variable interpolation
        │       └── FallbackHandler.java       # Progressive fallback logic
        └── resources/
            └── intents.json                   # Intent definitions
```

---

## 🚀 How to Build & Run

### Prerequisites
- **Java 17+** installed (`java -version`)
- **Maven** (A Maven Wrapper is included in the project)

### Build
Run tests and compile the project into a runnable fat JAR:
```bash
./mvnw clean package
```
*(On Windows, use `.\mvnw.cmd clean package`)*

### Run
```bash
java -jar target/gilgamesh-chatbot-1.0.0-shaded.jar
```

---

## ✏️ Customizing Intents

Edit `src/main/resources/intents.json` to add or modify intents:

```json
{
  "intents": [
    {
      "tag": "your_new_intent",
      "patterns": ["example phrase 1", "example phrase 2"],
      "responses": ["Response A {topic}", "Response B at {time}"]
    }
  ]
}
```

After editing, rebuild with `./mvnw clean package`.

**Tips for good patterns:**
- Include multiple variations of how a user might express the intent.
- The `SpellChecker` automatically populates its vocabulary from your patterns.

---

## 🛠 Tech Stack

- **Java 17**
- **Apache OpenNLP 2.4.0** — Tokenization
- **Gson 2.11.0** — JSON parsing
- **JUnit 5** — Automated testing
- **Maven** — Build & dependency management
