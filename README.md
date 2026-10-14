# vaadin-quixotic

UI examples for [Quixotic AI](https://qxotic.ai), built with [Vaadin](https://vaadin.com).

Everything lives in a single Spring Boot project — Chat, Vision, Embed, Audio,
Text to Speech, Speech to Text, and Live Transcribe — each backed by a local
model running through `jinfer-spring-ai`. No external APIs, no cloud calls:
inference runs entirely in Java.

## Requirements

- Java 25

## Running

Use the Maven wrapper included in the project — no local Maven install needed:

```bash
./mvnw
```

On Windows:

```bash
mvnw.cmd
```

The app starts on <http://localhost:8080>. Models are loaded lazily, one per
view, the first time that view is opened.

## Model downloads

Each view declares its own model as a Hugging Face `repo:quant` reference
(see the `app.*.model` properties in `src/main/resources/application.properties`).
On first use, Quixotic's `jinfer` runtime fetches the referenced GGUF file(s)
straight from Hugging Face (ModelScope is also supported as a source) and
caches them on local disk under a `jinfer/models` folder in the platform
cache directory — e.g. `~/.cache/jinfer/models` on Linux,
`~/Library/Caches/jinfer/models` on macOS, or `%LOCALAPPDATA%\jinfer\models`
on Windows (honors `XDG_CACHE_HOME` if set). Subsequent runs reuse the cached
files, so models are only downloaded once.

- Override the cache location with the `JINFER_MODELS` environment variable.
- Set `JINFER_OFFLINE=1` (or `-Djinfer.offline=true`) to force loading from
  the local cache only, without attempting a network fetch.
