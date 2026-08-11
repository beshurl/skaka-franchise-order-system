import json
import logging
import asyncio
from typing import List, Optional

from google import genai
from google.genai import types
from app.config.settings import settings
from app.model.schemas import (
    AiRecommendationPayload,
    CourseCategory,
    RecommendationItem,
)

logger = logging.getLogger(__name__)

CATEGORY_LABELS = {
    CourseCategory.FOOD: "간편식",
    CourseCategory.DRINK: "음료",
    CourseCategory.DAILY: "생활용품",
    CourseCategory.FRESH: "신선식품",
    CourseCategory.SNACK: "스낵",
    CourseCategory.HYGIENE: "위생용품",
    CourseCategory.CHILLED: "냉장식품",
    CourseCategory.OTHER: "기타",
}


class AiRanker:
    """백엔드가 선정한 후보 안에서만 추천 순위와 설명을 보완한다."""

    def __init__(self):
        self.enabled = bool(settings.gemini_api_key)
        self.client = (
            genai.Client(api_key=settings.gemini_api_key)
            if self.enabled
            else None
        )

    async def rank(
        self,
        candidates: List[RecommendationItem],
        dominant_category: Optional[CourseCategory],
        received_product_ids: List[int],
    ) -> Optional[AiRecommendationPayload]:
        if not self.client or not candidates:
            return None

        candidate_data = [
            {
                "productId": item.product.id,
                "name": item.product.title,
                "category": CATEGORY_LABELS[item.product.category],
                "supplyPrice": float(item.product.price),
                "headquartersStock": item.product.enrollmentCount,
                "baseScore": item.score,
            }
            for item in candidates
        ]

        prompt = {
            "role": (
                "당신은 편의점 본사 상품기획팀에서 가맹점의 다음 발주를 돕는 "
                "발주 분석 담당자입니다. 숫자를 나열하는 데 그치지 말고, 점주가 "
                "왜 이 상품을 지금 검토해야 하는지 이해할 수 있게 설명하세요."
            ),
            "task": (
                "규칙 기반 시스템이 선정한 후보 상품을 매장 입고 이력과 본사 재고를 "
                "근거로 재정렬하고, 상품별로 서로 다른 추천 사유를 작성하세요."
            ),
            "rules": [
                "candidates에 있는 productId만 사용",
                "최대 5개, productId 중복 금지",
                "판매량, 판매 흐름, 고객 반응, 예상 수요, 날씨, 계절, 고객 선호처럼 제공되지 않은 사실은 만들지 않기",
                "dominantCategory는 최근 입고 상품에서 가장 많이 확인된 카테고리라는 의미로만 사용",
                "headquartersStock은 본사에서 현재 발주 가능한 재고 수량으로만 해석",
                "baseScore는 규칙 기반 적합도이며 점수 자체보다 점수가 높거나 낮은 이유를 설명",
                "score는 해당 후보의 baseScore와 반드시 같은 값으로 출력",
                "reason은 자연스러운 한국어 두 문장으로 90~150자 내외",
                "첫 문장은 매장 입고 이력 또는 주력 카테고리와의 관계를 설명",
                "둘째 문장은 본사 재고와 적합도를 연결해 점주가 취할 발주 판단을 제안",
                "상위 5개 reason의 문장 시작과 표현을 반복하지 않기",
                "'추천합니다', '검토하기 좋습니다', '후보입니다'만 반복하는 단조로운 문장 금지",
                "본사 재고 50개 이상은 비교적 여유, 20~49개는 보통, 20개 미만은 제한적으로 해석",
                "재고가 낮으면 빠른 확보를 권하지 말고 발주 가능 수량 확인이나 최소 수량 검토를 제안",
                "내부 영문 카테고리 코드나 필드명은 reason과 signals에 절대 노출하지 않기",
                "signals는 '최근 입고 주력: 음료', '본사 재고 72개', '규칙 적합도 94점'처럼 한국어로 작성",
                "JSON 객체만 출력",
            ],
            "writingExamples": [
                {
                    "situation": "주력 카테고리와 일치하고 본사 재고가 충분한 경우",
                    "reason": (
                        "최근 입고 이력에서 음료 비중이 높아 기존 매장 운영 흐름과 자연스럽게 이어지는 상품입니다. "
                        "본사 재고도 안정적인 편이므로 다음 발주에서 우선 수량을 검토할 만합니다."
                    ),
                },
                {
                    "situation": "주력 카테고리와 다르지만 구색 보완에 도움이 되는 경우",
                    "reason": (
                        "기존 입고가 특정 카테고리에 집중되어 있어 상품 구성을 보완하는 선택지가 될 수 있습니다. "
                        "현재 확보된 본사 재고와 적합도를 고려해 소량부터 반응을 확인해 보세요."
                    ),
                },
            ],
            "outputSchema": {
                "recommendations": [
                    {
                        "productId": "number",
                        "score": "0-100 integer",
                        "reason": "string",
                        "signals": ["string"],
                    }
                ]
            },
            "storeContext": {
                "receivedProductIds": received_product_ids,
                "dominantCategory": (
                    CATEGORY_LABELS[dominant_category] if dominant_category else None
                ),
                "dominantCategoryMeaning": (
                    "최근 입고 상품 중 가장 많이 확인된 카테고리"
                    if dominant_category
                    else "입고 이력이 부족해 주력 카테고리를 판단할 수 없음"
                ),
            },
            "candidates": candidate_data,
        }

        try:
            response = await asyncio.wait_for(
                self.client.aio.models.generate_content(
                    model=settings.gemini_model,
                    contents=json.dumps(prompt, ensure_ascii=False),
                    config=types.GenerateContentConfig(
                        response_mime_type="application/json",
                        response_schema=AiRecommendationPayload,
                        max_output_tokens=4096,
                        temperature=0.2,
                        thinking_config=types.ThinkingConfig(
                            thinking_level=types.ThinkingLevel.LOW,
                        ),
                    ),
                ),
                timeout=settings.gemini_timeout_seconds,
            )
            return AiRecommendationPayload.model_validate_json(response.text)
        except Exception as exc:
            logger.warning("[AiRanker] Gemini 추천 실패, 규칙 기반으로 전환: %s", exc)
            return None


ai_ranker = AiRanker()
