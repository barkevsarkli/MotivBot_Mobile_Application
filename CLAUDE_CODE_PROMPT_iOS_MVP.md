# Claude Code Prompt — MotivBot: XLM-R inference MVP (local backend + iOS)

> Paste everything below the line into Claude Code, opened at the repo root
> (`/Users/khaos/Desktop/khaos/MotivBot`).

---

You are working in the MotivBot repo, a TÜBİTAK-funded mental-wellbeing app. Your job is to replace the OpenAI/GPT placeholder in the **iOS app** with our own trained classifier from Hugging Face. The classifier will be served by a **local FastAPI service** for this MVP demo.

**Before writing any code:** read the files listed under "Read first", then show me a short implementation plan and any open questions. Wait for my approval before you start editing. Ask me whenever something here conflicts with what you find in the code.

## Repo layout

- `MotivBot/`: iOS app (SwiftUI, `@Observable` view model, iOS 26.2 target, generated Info.plist). Xcode project: `MotivBot.xcodeproj`.
- `MotivBot_final/`: Android app (Kotlin). **Do not touch it in this task.**
- `Testing_amirbhat_motivbot_multilingual_XLMR_10062026v3_adlı_not_defterinin_kopyası.ipynb`: reference inference notebook. This is the source of truth for the model class and inference.
- `MotivBotTests/`, `MotivBotUITests/`: iOS tests.

## Read first

- The notebook, especially cells 1, 3, 5 and 7: config, the `MentalHealthClassifier` class, loading, and `predict_with_metadata`.
- `MotivBot/Models.swift`: `TestData.sections`, question IDs, option values, `MotivationCard`.
- `MotivBot/WellBeingViewModel.swift`: `submitToAPI`, `buildPrompt`, card state, `generateCSV`, restart.
- `MotivBot/OpenAIService.swift`, `MotivBot/ResultView.swift`, `MotivBot/LoadingView.swift`, `MotivBot/ContentView.swift`.
- `MotivBot/{en,tr,fa}.lproj/Localizable.strings`.
- Run `git status`. The repo has **staged, uncommitted renames** (MindWell → MotivBot). Do not commit, unstage or reset them without asking me first. Work on a new branch, `feature/xlmr-inference-mvp`.

## The model (facts)

- HF repo `amirbhat/motivbot-multilingual-XLMR-10062026v3`: public, about 1.1 GB, F32 safetensors, loaded via `PyTorchModelHubMixin`.
- It is a **classifier, not an LLM**. It takes the CLS output of `xlm-roberta-base` (768 dims) plus 4 structured features `[age, sex, rses, phq]`. These pass through a fusion layer (512) and then **5 heads × 3 classes**, `['No','Low','High']`.
- The heads, in this order: `Anxiety_Fear_Worry`, `Depression_Hopelessness_Sadness`, `Self_Esteem_Issue`, `Positive_Affect`, `Negative_Affect`.
- Inference settings: `MAX_LEN=256`, `padding='max_length'`, `truncation=True`, softmax over `logits / temperature` with `temperature=0.45`.
- Training data columns: `Age`, `Gender`, `Self_Description` (Turkish/Persian free text), `PHQ-9` (total score), `Rosenberg.self-esteem` (total 10–40, higher = better self-esteem).

## Product decisions (already made)

1. **GPT is removed completely.** No OpenAI calls remain anywhere in the iOS app, and no motivation cards are generated.
2. **The backend runs locally only, for a demo.** The iOS Simulator talks to `http://localhost:8000`. Make the URL configurable so a real device on the same Wi-Fi can use the Mac's LAN IP.
3. **Results are shown in soft, supportive language.** Never show diagnosis-like wording such as "Depression: High", and do not show raw percentages to the user. Show a short "this is not a diagnosis" note. When risk is elevated, show a support card.
4. **iOS only** for now. Android comes later, so keep the API contract platform-neutral.

## Part 1: Inference service (`motivbot-inference/` at repo root)

Stack: Python 3.11, FastAPI, uvicorn, CPU `torch`, `transformers`, `huggingface_hub`, `safetensors`, `numpy`, `pydantic` v2, `pytest`. Pin versions in `requirements.txt`.

Suggested structure:

```
motivbot-inference/
  app/
    main.py            # FastAPI app; loads the model once in lifespan
    model.py           # MentalHealthClassifier copied verbatim from the notebook + predict_raw()
    scoring.py         # raw app answers -> dataset columns (pure functions, no torch)
    schemas.py         # pydantic request/response models
    config.py          # all tunables, overridable via env vars
  tests/
    test_scoring.py
    test_golden.py     # marked @pytest.mark.model (needs model download)
    test_api.py        # FastAPI TestClient with the model mocked
  requirements.txt
  run.sh               # uvicorn app.main:app --host 0.0.0.0 --port 8000
  README.md
```

### Model loading

- Copy the `MentalHealthClassifier` class **exactly** as it appears in the notebook, with the same layer names so the state dict matches. Load it with `MentalHealthClassifier.from_pretrained(MODEL_ID)` and `AutoTokenizer.from_pretrained(MODEL_ID)`, then call `.eval()` and run under `torch.no_grad()`.
- Note: `__init__` also downloads `xlm-roberta-base`. That is fine for the MVP. Do not "optimise" it unless the golden test still passes afterwards.
- Load the model in the FastAPI lifespan. `GET /health` returns `{"status":"loading"|"ready","model_id":...}`. `/predict` returns 503 while the model is loading.
- The first run downloads about 2.2 GB into the HF cache. Say so in the README.

### Scoring: app answers → dataset columns

This is the **single source of truth** for scoring. The app sends raw answers, and only the server computes the totals.

| Dataset column | App source | Rule |
|---|---|---|
| `Self_Description` | emotion-input text | Trim it; reject if empty; the tokenizer truncates at 256 tokens |
| `Age` | `qa_1` (free text, question id 1) | Integer 10–100, otherwise 422 |
| `Gender` → `sex` | `qa_2` (id 2): option value 1 = Man, 2 = Woman, 3 = Prefer not to say | Request field `gender: "male"\|"female"\|"unspecified"`. Numeric mapping comes from config: **male = 0.0, female = 1.0, unspecified = 0.5** (an assumption, see below) |
| `PHQ-9` | `qb_1…qb_9` (ids 7–15), values 0–3 | Sum = 0–27. **`qb_10` (id 16) is an attention check ("Please answer 'Not at all'") and must NOT be included.** Exactly 9 items, each 0–3 |
| `Rosenberg.self-esteem` | `qd_1…qd_10` (ids 24–33). App values: Strongly agree = 3, Agree = 2, Disagree = 1, Strongly disagree = 0 | Positively worded items 1, 2, 4, 6, 7 → `value + 1`. Negatively worded items 3, 5, 8, 9, 10 → `4 - value`. Total 10–40 |

- The **current app does not reverse-score RSES**, and that is a bug for our purposes. Before coding, check the item wording in all three `Localizable.strings` files to confirm which items are negatively worded. The order must be the same in en, tr and fa.
- `config.py` must expose: `GENDER_MAP`, `NORMALIZE_METADATA` (default **False**; raw values, as in the notebook's test cell), placeholder `METADATA_MEAN`/`METADATA_STD` (used only when normalisation is on), `TEMPERATURE=0.45` and `MAX_LEN=256`.
- Add a comment: *"The gender encoding and normalisation are unconfirmed assumptions pending the model author (Amir). Change them only here."*

### API contract

`POST /predict`

Request:

```json
{
  "text": "Herkes beni sinirlendiriyor, artık tahammülüm kalmadı",
  "age": 25,
  "gender": "male",
  "phq9_items": [1,2,1,0,2,1,1,2,0],
  "rses_items": [2,2,1,2,1,2,2,1,1,1],
  "language": "tr"
}
```

`rses_items` are the raw app values (0–3, Strongly agree = 3). The server applies the reverse scoring. `language` is informational only.

Response:

```json
{
  "model_id": "amirbhat/motivbot-multilingual-XLMR-10062026v3",
  "inputs_used": {"Age": 25, "sex": 0.0, "PHQ-9": 10, "Rosenberg.self-esteem": 21},
  "dimensions": [
    {"key": "Anxiety_Fear_Worry", "level": "High",
     "probabilities": {"No": 0.05, "Low": 0.07, "High": 0.88}}
  ],
  "elevated_risk": false
}
```

- `dimensions` always contains all 5 keys, in head order. `level` is the argmax.
- `elevated_risk = true` if `phq9_items[8] >= 1` (PHQ item 9, thoughts of self-harm) **or** `Depression_Hopelessness_Sadness.level == "High"`.
- Return 422 with clear messages for invalid input. Never log the free text, because it is sensitive. Logging only lengths and timings is fine.
- Enable CORS for local development only.

### Tests (must pass)

- `test_scoring.py`: PHQ sum excludes the attention check; RSES all-"Strongly agree" → 25 (5×4 + 5×1); all-"Strongly disagree" → 25; the best-case pattern → 40; the worst-case pattern → 10; wrong item counts or out-of-range values → error; gender mapping.
- `test_golden.py`: call `predict_raw(text, age=25, sex=0, rses=18, phq=12)` with the sentence above. The result must match the notebook output within ±0.5 percentage points:
  - Anxiety High 88.6%
  - Depression No 84.5%
  - Self-Esteem No 93.6%
  - Positive Affect No 48.3%
  - Negative Affect High 38.5%

  **If this test fails, stop and tell me. Do not tweak the preprocessing to force it to pass.**
- `test_api.py`: request/response shape, 422 cases, and 503 while loading. Mock the model here.

## Part 2: iOS app

### Networking

- **Delete `OpenAIService.swift`.** It contains a hard-coded OpenAI key. Remove every other reference to OpenAI, GPT, `apiKey` and `buildPrompt` too. (I will revoke the key myself. Just remind me that it is still in git history.)
- Add `MotivBotAPIService.swift`: async/await `URLSession`, `Codable` request/response models that mirror the contract exactly, a 120 s timeout (the first request can be slow), and typed errors (`serverUnreachable`, `modelLoading`, `invalidInput(String)`, `decoding`, `http(Int)`).
- Base URL: read `MotivBotAPIBaseURL` from Info.plist (the project uses `GENERATE_INFOPLIST_FILE`, so add it as an `INFOPLIST_KEY_*` build setting or a small Info.plist, whichever is cleaner). Default to `http://localhost:8000`.
- Allow local HTTP via ATS with `NSAllowsLocalNetworking` only. Do **not** use `NSAllowsArbitraryLoads`.
- Optionally poll `/health` before calling `/predict` so the user sees a "model is waking up" state instead of a timeout.

### View model (`WellBeingViewModel.swift`)

- Build the request from the existing answer storage using the question IDs in the table above: `emotionText` → `text`, id 1 → `age`, id 2 → `gender`, ids 7–15 → `phq9_items`, ids 24–33 → `rses_items` (raw values). Put this mapping in a separate, pure, testable function (e.g. `PredictionRequestBuilder`).
- Check how the age input is validated today. If a non-numeric or out-of-range age can get through, block it in the questionnaire with a localized message, not at submit time.
- Replace the card state (`motivationCards`, `currentCardIndex`, swipe ratings, `fallbackCards`, `padded`, `allCardsReviewed`) with `prediction: PredictionResponse?`, plus loading and error states with retry.
- Keep the CSV/mail export, restart, theme and language features. Add the model's levels and `inputs_used` to the CSV, since researchers need them.
- If the "reflection" feature only exists to follow the cards, ask me whether to keep it before removing it.

### Result screen (`ResultView.swift`)

- Remove the card deck and swipe UI, then remove `MotivationCard` and `SwipeDirection` if nothing else uses them.
- Show one row per dimension: an icon, a soft title, a 3-step level indicator and one supportive sentence. **Do not show percentages to the user.**
- Note that `Positive_Affect` is a positive trait (High = good). The other four are concerns (High = more difficulty). Pick colours and wording accordingly.
- Wording must be gentle and non-diagnostic. Examples (en):
  - Anxiety: No → "You seem fairly calm right now."
  - Anxiety: High → "It sounds like worry is taking up a lot of space lately."
  - Never use "you have depression" or similar.
- Show a fixed note: "This is not a diagnosis. These results are a reflection of what you shared today."
- If `elevated_risk` is true, show a support card at the **top** of the screen. It should encourage the user to talk to someone they trust or a mental-health professional. **Do not hard-code helpline numbers.** Add localized placeholder keys (e.g. `support_resources_body`) marked with a `// TODO: text to be approved by the psychology team` comment.
- Add every new string to **en, tr and fa** `Localizable.strings`. Check that fa renders right-to-left correctly.
- Keep the existing visual style (colours, theme toggle, typography).

### Loading and errors (`LoadingView.swift`)

- Reuse the existing loading screen. Show "Analysing…", and "Model is starting up, this can take a minute" when `/health` reports `loading`.
- If the server is unreachable, show a friendly message with a Retry button. Do not crash and do not get stuck.

### iOS tests

- `MotivBotTests`:
  - The request builder maps a fully answered questionnaire to the exact JSON. Cover the excluded attention check, raw RSES values and gender mapping.
  - `PredictionResponse` decodes a sample JSON.
  - The `elevated_risk` flag reaches the view model state.
- The project must build with no warnings related to these changes.

## Definition of done

1. `cd motivbot-inference && ./run.sh` starts the service; `GET /health` → `ready`; `pytest` passes, including the golden test.
2. The iOS app builds and runs on the Simulator against localhost. A full run-through (emotion text → questionnaire → result screen) shows the 5 soft-language rows, and the support card when PHQ item 9 > 0.
3. `grep -ri "openai\|gpt\|sk-proj" MotivBot/` returns nothing.
4. Android (`MotivBot_final/`) is untouched.
5. A short `motivbot-inference/README.md` explains: setup (venv, install), first-run download size, running the service, running the tests, using a real iPhone (Mac LAN IP + `MotivBotAPIBaseURL`), and the open assumptions (gender encoding, normalisation, temperature).
6. Commits on `feature/xlmr-inference-mvp` are split logically: backend, iOS networking, iOS UI, tests. Show me `git status` before the first commit.

## Out of scope

Android, cloud deployment (HF Space or a server), authentication, analytics, model retraining, and changing the questionnaire content.
