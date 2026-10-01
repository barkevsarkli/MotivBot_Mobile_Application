# MotivBot inference service (local MVP)

Local FastAPI service that serves the MotivBot XLM-R classifier
([`amirbhat/motivbot-multilingual-XLMR-10062026v3`](https://huggingface.co/amirbhat/motivbot-multilingual-XLMR-10062026v3))
to the iOS app. Demo use only: no authentication, CORS open to localhost.

The model is a classifier, not an LLM. It reads the free-text self description plus
`[age, sex, Rosenberg total, PHQ-9 total]` and outputs `No / Low / High` for five dimensions.

## Setup

Requires Python 3.11.

```bash
cd motivbot-inference
python3.11 -m venv .venv
.venv/bin/pip install -r requirements.txt
```

### First run downloads about 2.2 GB

On the first start the service downloads into the Hugging Face cache (`~/.cache/huggingface/hub`):

- the MotivBot weights (about 1.1 GB), and
- `xlm-roberta-base` (about 1.1 GB), which `MentalHealthClassifier.__init__` loads before the
  MotivBot weights replace it.

Later starts load from the cache in about 15 seconds. `app/__init__.py` sets
`HF_HUB_DISABLE_XET=1` because the legacy `xlm-roberta-base` repo id returns 404 on the Xet
download path; plain HTTP downloads work.

## Run

```bash
./run.sh            # uvicorn on 0.0.0.0:8000 (PORT=9000 ./run.sh to change)
curl localhost:8000/health
# {"status":"loading", ...}  then  {"status":"ready", ...}
```

`/predict` returns `503` (with `Retry-After: 10`) until the model is ready. If loading fails,
`/health` reports `"error"` and the log has the traceback.

### API

`POST /predict`

```json
{
  "text": "Herkes beni sinirlendiriyor, artık tahammülüm kalmadı",
  "age": 25,
  "gender": "male",
  "phq9_items": [1, 2, 1, 0, 2, 1, 1, 2, 0],
  "rses_items": [2, 2, 1, 2, 1, 2, 2, 1, 1, 1],
  "language": "tr"
}
```

- `gender`: `male` | `female` | `unspecified`
- `phq9_items`: PHQ-9 items 1–9, each 0–3. The app's attention check (`qb_10`) is not sent.
- `rses_items`: Rosenberg items 1–10 as raw app values (Strongly agree = 3 … Strongly disagree = 0).
  The server reverse-scores items 3, 5, 8, 9, 10 and returns a total of 10–40.
- `language`: informational only.

The response has `inputs_used` (the exact model inputs), `dimensions` (all five heads in model
order, each with `level` = argmax and `probabilities`) and `elevated_risk`
(`true` if PHQ item 9 ≥ 1 or `Depression_Hopelessness_Sadness` is `High`).
Invalid input returns `422` with a readable `detail` string. The free text is never logged or echoed.

## Tests

```bash
.venv/bin/python -m pytest                 # everything, including the golden test
.venv/bin/python -m pytest -m "not model"  # fast: scoring + API with a mocked model
```

`tests/test_golden.py` runs the real model on the notebook example and checks it against the
notebook output within ±0.5 percentage points.

## Using a real iPhone

1. Mac and iPhone on the same Wi-Fi. Start the service with `./run.sh` (it binds to `0.0.0.0`).
2. Find the Mac's LAN IP: `ipconfig getifaddr en0` (for example `192.168.1.23`).
3. In Xcode, select the **MotivBot** target → Build Settings → set `MOTIVBOT_API_BASE_URL` to
   `http://192.168.1.23:8000`. It is written into Info.plist as `MotivBotAPIBaseURL`.
4. On first launch, iOS asks for Local Network access. Allow it.
5. macOS may ask whether Python can accept incoming connections. Allow it.

ATS allows plain HTTP only for local networking (`NSAllowsLocalNetworking`), so this works for
LAN IPs and `localhost` but not for public hosts.

## Model inputs: confirmed and open

All of these live in `app/config.py` and can be overridden with env vars.

| Setting | Current value | Status |
|---|---|---|
| `NORMALIZE_METADATA` | `False` (raw values) | Confirmed by the model author: training used raw inputs; normalisation hurt performance. |
| `GENDER_MAP` | male 0.0, female 1.0, unspecified 0.5 | male/female confirmed. "unspecified" = 0.5 is our assumption; training data only had 0 and 1. |
| Text cleaning | collapse whitespace, trim | Confirmed as "basic cleaning such as removing extra white space". Any further cleaning steps are still to be confirmed. |
| `TEMPERATURE` | 0.45 | Taken from the notebook. Not yet confirmed for this checkpoint. |
| `MAX_LEN` | 256 | Taken from the notebook. |

Env vars: `MOTIVBOT_MODEL_ID`, `MOTIVBOT_TEMPERATURE`, `MOTIVBOT_MAX_LEN`,
`MOTIVBOT_GENDER_MAP` (JSON), `MOTIVBOT_NORMALIZE_METADATA`, `MOTIVBOT_METADATA_MEAN` (JSON list),
`MOTIVBOT_METADATA_STD` (JSON list), `MOTIVBOT_CORS_ORIGINS` (comma-separated).
