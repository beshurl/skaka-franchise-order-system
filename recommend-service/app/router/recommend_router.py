import logging
from fastapi import APIRouter, Depends
from app.config.security import verify_token
from app.model.schemas import RecommendResponse
from app.service.recommend_service import recommend_service

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/api/recommend", tags=["recommend"])


@router.get("/{user_id}", response_model=RecommendResponse)
async def get_recommendations(
    user_id: int,
    token_payload: dict = Depends(verify_token)
):
    """
    GET /api/recommend/{storeId} - 가맹점 기반 상품 추천

    - 입고 이력과 활성 상품으로 안전한 후보를 먼저 선정
    - Gemini 설정 시 후보 안에서 순위와 추천 근거 보완
    - API 키가 없거나 AI 호출 실패 시 규칙 기반 결과 반환
    """
    logger.info(f"[Router] 추천 요청 - userId: {user_id}")
    return await recommend_service.get_recommendations(user_id)


@router.get("/health", include_in_schema=False)
async def health_check():
    return {"status": "UP", "service": "recommend-service"}
