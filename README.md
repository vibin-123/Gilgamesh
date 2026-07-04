# ⚔ Gilgamesh — Java NLP Chatbot

A Java-based chatbot that demonstrates **core Natural Language Processing (NLP) concepts** without calling any external APIs. Built with **Apache OpenNLP** for tokenization and **Jaccard similarity scoring** for intent classification.

---

## 🏗 Architecture

```
User Input (raw text)
      │
      ▼
┌──────────────────┐
│ TextPreprocessor  │  ← Tokenization (OpenNLP), Lowercasing, Stopword Removal
└───────┬──────────┘
        ▼
┌──────────────────┐     ┌──────────────────┐
│ IntentClassifier  │◄────│ IntentRepository  │  ← Loads intents.json
│ (Jaccard Scoring) │     └──────────────────┘
└───────┬──────────┘
        ▼
┌──────────────────┐
│  ResponseEngine   │  ← Random template-based response selection
└───────┬──────────┘
        ▼
   Bot Response
```

### Component Breakdown

| Class | Package | NLP Role |
|-------|---------|----------|
| `Main.java` | `com.gilgamesh` | Entry point — wires components, runs the chat REPL |
| `TextPreprocessor.java` | `com.gilgamesh.nlp` | **Tokenization** (OpenNLP), **case normalization**, **stopword removal** |
| `IntentClassifier.java` | `com.gilgamesh.nlp` | **Intent classification** via Jaccard similarity (bag-of-words) |
| `IntentRepository.java` | `com.gilgamesh.data` | Loads & parses training data from `intents.json` |
| `ResponseEngine.java` | `com.gilgamesh.response` | **Response generation** — template-based with random selection |
| `Intent.java` | `com.gilgamesh.model` | Data model representing an intent (tag, patterns, responses) |

---

## 🧠 NLP Concepts Demonstrated

### 1. Tokenization
Splitting raw text into individual tokens (words). We use **OpenNLP's `SimpleTokenizer`** which handles punctuation-aware splitting — e.g., `"hello!"` → `["hello", "!"]` instead of treating it as one token.

### 2. Case Normalization
Converting all text to lowercase so that `"Hello"`, `"HELLO"`, and `"hello"` are treated identically. Essential for consistent matching.

### 3. Stopword Removal
Filtering out common words (`the`, `is`, `a`, `in`, etc.) that carry little semantic meaning. This focuses the classifier on content-bearing keywords. For example:
- `"what is the weather today"` → `["weather", "today"]`

### 4. Bag-of-Words Representation
Treating text as an unordered set of words, ignoring grammar and word order. This simplification is sufficient for intent classification where keywords matter more than structure.

### 5. Jaccard Similarity Scoring
Measuring the overlap between two word sets:

```
J(A, B) = |A ∩ B| / |A ∪ B|
```

Produces a score between `0.0` (no overlap) and `1.0` (identical). The intent whose pattern produces the highest score is selected.

### 6. Confidence Thresholding
If the best Jaccard score falls below `0.15`, the input is considered unrecognized and a fallback response is returned. This prevents false matches on unrelated inputs.

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
        │   ├── nlp/
        │   │   ├── TextPreprocessor.java       # Tokenization + stopwords
        │   │   └── IntentClassifier.java       # Jaccard-based classifier
        │   ├── data/
        │   │   └── IntentRepository.java       # JSON data loader
        │   └── response/
        │       └── ResponseEngine.java         # Response selection
        └── resources/
            └── intents.json                   # Intent definitions
```

---

## 🚀 How to Build & Run

### Prerequisites
- **Java 17+** installed (`java --version`)
- **Maven 3.8+** installed (`mvn --version`)

### Build
```bash
mvn clean package
```

This compiles the project and creates a runnable fat JAR in `target/`.

### Run
```bash
java -jar target/gilgamesh-chatbot-1.0.0.jar
```

### Example Conversation
```
╔══════════════════════════════════════════════════╗
║           ⚔  GILGAMESH NLP CHATBOT  ⚔          ║
║                                                  ║
║  A Java chatbot demonstrating core NLP concepts  ║
║  Powered by Apache OpenNLP                       ║
╚══════════════════════════════════════════════════╝

Type your message below. Type 'quit' to exit.

You: Hello there!
Gilgamesh: Hey there! What's on your mind?

You: Tell me a joke
Gilgamesh: Why do Java developers wear glasses? Because they can't C#! 😄

You: What is NLP?
Gilgamesh: NLP is how machines make sense of human text. In my case, I tokenize your input, remove stopwords, and match your words against known intent patterns!

You: quit
Gilgamesh: Goodbye! It was great chatting with you. 👋
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
      "responses": ["Response A", "Response B"]
    }
  ]
}
```

After editing, rebuild with `mvn clean package`.

**Tips for good patterns:**
- Include multiple variations of how a user might express the intent
- Use short, keyword-rich phrases
- The more patterns, the better the classifier can match

---

## 📖 Further Learning

Want to extend this project? Here are some ideas:

| Enhancement | Concept |
|-------------|---------|
| Add TF-IDF weighting | Rare words become more important than common ones |
| Use OpenNLP `DocumentCategorizer` | Train a real ML classifier from the patterns |
| Add stemming/lemmatization | Treat "running", "runs", "ran" as the same word |
| Implement context tracking | Remember previous messages for multi-turn conversations |
| Add entity extraction | Pull out specific values (dates, names) from user input |

---

## 🛠 Tech Stack

- **Java 17**
- **Apache OpenNLP 2.4.0** — Tokenization
- **Gson 2.11.0** — JSON parsing
- **Maven** — Build & dependency management
