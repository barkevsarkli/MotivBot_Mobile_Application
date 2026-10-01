"""FastAPI app. The model loads once, in the background, when the app starts."""

import logging
import threading
import time
from contextlib import asynccontextmanager
from typing import Callable, Protocol

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from app import config
from app.schemas import Dimension, HealthResponse, InputsUsed, PredictRequest, PredictResponse, Probabilities
from app.scoring import ScoringError, build_model_inputs

logger = logging.getLogger("motivbot.inference")


class SupportsPredictRaw(Protocol):
    def predict_raw(self, text: str, age: float, sex: float, rses: float, phq: float) -> dict[str, list[float]]: ...


class ModelState:
    def __init__(self) -> None:
        self.status = "loading"
        self.predictor: SupportsPredictRaw | None = None
        self.lock = threading.Lock()  # one forward pass at a time on CPU


def _default_loader() -> SupportsPredictRaw:
    from app.model import Predictor  # imported lazily so tests never import torch

    return Predictor(config.MODEL_ID)


def _load_in_background(state: ModelState, loader: Callable[[], SupportsPredictRaw]) -> None:
    started = time.perf_counter()
    try:
        state.predictor = loader()
        state.status = "ready"
        logger.info("model ready in %.1fs", time.perf_counter() - started)
    except Exception:
        state.status = "error"
        logger.exception("model failed to load")


def create_app(loader: Callable[[], SupportsPredictRaw] | None = _default_loader) -> FastAPI:
    """Pass loader=None to skip loading (tests set app.state.model directly)."""
    state = ModelState()

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        if loader is not None:
            threading.Thread(target=_load_in_background, args=(state, loader), daemon=True).start()
        yield

    app = FastAPI(title="MotivBot inference", version="0.1.0", lifespan=lifespan)
    app.state.model = state
    app.add_middleware(
        CORSMiddleware,
        allow_origins=config.CORS_ORIGINS,
        allow_methods=["GET", "POST"],
        allow_headers=["Content-Type"],
    )

    @app.exception_handler(RequestValidationError)
    async def validation_error(_: Request, exc: RequestValidationError) -> JSONResponse:
        # Never echo the submitted values back; the text is sensitive.
        messages = []
        for err in exc.errors():
            location = ".".join(str(p) for p in err["loc"] if p != "body")
            messages.append(f"{location}: {err['msg']}" if location else err["msg"])
        return JSONResponse(status_code=422, content={"detail": "; ".join(messages)})

    @app.exception_handler(ScoringError)
    async def scoring_error(_: Request, exc: ScoringError) -> JSONResponse:
        return JSONResponse(status_code=422, content={"detail": str(exc)})

    @app.get("/health", response_model=HealthResponse)
    def health() -> HealthResponse:
        return HealthResponse(status=app.state.model.status, model_id=config.MODEL_ID)

    @app.post("/predict", response_model=PredictResponse, response_model_by_alias=True)
    def predict(body: PredictRequest):
        model_state: ModelState = app.state.model
        if model_state.status != "ready" or model_state.predictor is None:
            return JSONResponse(
                status_code=503,
                content={"detail": f"model is {model_state.status}"},
                headers={"Retry-After": "10"},
            )

        inputs = build_model_inputs(body.text, body.age, body.gender, body.phq9_items, body.rses_items)

        started = time.perf_counter()
        with model_state.lock:
            raw = model_state.predictor.predict_raw(inputs.text, inputs.age, inputs.sex, inputs.rses, inputs.phq)
        logger.info("predict text_len=%d took %.0fms", len(inputs.text), (time.perf_counter() - started) * 1000)

        dimensions = []
        for key in config.HEADS:
            probs = raw[key]
            level = config.LEVELS[max(range(len(probs)), key=probs.__getitem__)]
            dimensions.append(
                Dimension(key=key, level=level, probabilities=Probabilities(**dict(zip(config.LEVELS, probs))))
            )

        depression = next(d for d in dimensions if d.key == "Depression_Hopelessness_Sadness")
        elevated_risk = body.phq9_items[8] >= 1 or depression.level == "High"

        return PredictResponse(
            model_id=config.MODEL_ID,
            inputs_used=InputsUsed(age=inputs.age, sex=inputs.sex, phq9=inputs.phq, rosenberg=inputs.rses),
            dimensions=dimensions,
            elevated_risk=elevated_risk,
        )

    return app


logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
logging.getLogger("httpx").setLevel(logging.WARNING)
app = create_app()
