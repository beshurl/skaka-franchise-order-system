import logging
from collections import Counter
from typing import Dict, List, Optional, Tuple

from app.client.course_client import course_client
from app.client.enrollment_client import enrollment_client
from app.config.settings import settings
from app.model.schemas import (
    CourseCategory,
    CourseResponse,
    RecommendResponse,
    RecommendationItem,
)
from app.service.ai_ranker import ai_ranker

logger = logging.getLogger(__name__)


CATEGORY_LABELS: Dict[CourseCategory, str] = {
    CourseCategory.BACKEND: "간편식",
    CourseCategory.FRONTEND: "음료",
    CourseCategory.DEVOPS: "생활용품",
    CourseCategory.DATA_SCIENCE: "신선식품",
    CourseCategory.MOBILE: "스낵",
    CourseCategory.SECURITY: "위생용품",
    CourseCategory.DATABASE: "냉장식품",
    CourseCategory.OTHER: "기타",
}


class RecommendService:
    """
    가맹점 입고 이력과 상품 정보를 기반으로 후보를 만들고,
    Gemini가 설정된 경우 후보 안에서 순위와 추천 근거를 보완한다.
    """

    MAX_RECOMMEND_COUNT = 5
    MAX_AI_CANDIDATE_COUNT = 8

    async def get_recommendations(self, user_id: int) -> RecommendResponse:
        logger.info("[RecommendService] 추천 시작 - storeId: %s", user_id)

        history = await enrollment_client.get_enrollment_history(user_id)
        received_ids = history.receivedProductIds
        all_products = await course_client.get_all_courses()
        active_products = [p for p in all_products if p.status == "ACTIVE"]

        dominant_category = self._find_dominant_category(
            received_ids, active_products
        )
        candidates = [p for p in active_products if p.id not in received_ids]
        if not candidates:
            candidates = active_products

        fallback_items = self._rank_candidates(candidates, dominant_category)
        final_items, analysis_mode = await self._apply_ai_ranking(
            fallback_items,
            dominant_category,
            received_ids,
        )

        if not final_items:
            message = "현재 추천할 수 있는 활성 상품이 없습니다."
        elif analysis_mode == "AI":
            message = "입고 이력과 상품 데이터를 AI가 분석한 발주 추천입니다."
        else:
            message = "입고 이력과 상품 데이터를 기준으로 계산한 발주 추천입니다."

        return RecommendResponse(
            userId=user_id,
            recommendedCourses=[item.product for item in final_items],
            recommendations=final_items,
            basedOnCategory=dominant_category,
            message=message,
            analysisMode=analysis_mode,
            model=settings.gemini_model if analysis_mode == "AI" else None,
        )

    def _find_dominant_category(
        self,
        product_ids: List[int],
        products: List[CourseResponse],
    ) -> Optional[CourseCategory]:
        product_map = {product.id: product for product in products}
        categories = [
            product_map[product_id].category
            for product_id in product_ids
            if product_id in product_map
        ]
        most_common = Counter(categories).most_common(1)
        return most_common[0][0] if most_common else None

    def _rank_candidates(
        self,
        products: List[CourseResponse],
        dominant_category: Optional[CourseCategory],
    ) -> List[RecommendationItem]:
        ranked: List[RecommendationItem] = []

        for product in products:
            category_match = dominant_category == product.category
            stock_signal = max(0, min(product.enrollmentCount, 100))
            score = min(100, 50 + (30 if category_match else 0) + stock_signal // 5)
            category_label = CATEGORY_LABELS[product.category]

            if category_match:
                reason = (
                    f"최근 입고한 {category_label} 상품과 같은 분류로 함께 검토하기 좋습니다."
                )
                signals = ["최근 입고 카테고리 일치", "현재 발주 가능"]
            else:
                reason = (
                    f"현재 발주 가능한 {category_label} 상품으로 품목 구성을 넓힐 수 있습니다."
                )
                signals = ["현재 발주 가능", f"재고 지표 {product.enrollmentCount}"]

            ranked.append(
                RecommendationItem(
                    product=product,
                    score=score,
                    reason=reason,
                    signals=signals,
                )
            )

        return sorted(
            ranked,
            key=lambda item: (item.score, item.product.enrollmentCount),
            reverse=True,
        )[: self.MAX_AI_CANDIDATE_COUNT]

    async def _apply_ai_ranking(
        self,
        fallback_items: List[RecommendationItem],
        dominant_category: Optional[CourseCategory],
        received_ids: List[int],
    ) -> Tuple[List[RecommendationItem], str]:
        ai_payload = await ai_ranker.rank(
            fallback_items,
            dominant_category,
            received_ids,
        )
        if not ai_payload:
            return fallback_items[: self.MAX_RECOMMEND_COUNT], "RULE_BASED"

        candidate_map = {item.product.id: item for item in fallback_items}
        selected: List[RecommendationItem] = []
        selected_ids = set()

        for ai_item in ai_payload.recommendations:
            fallback = candidate_map.get(ai_item.productId)
            if not fallback or ai_item.productId in selected_ids:
                continue
            selected.append(
                RecommendationItem(
                    product=fallback.product,
                    score=fallback.score,
                    reason=ai_item.reason,
                    signals=ai_item.signals[:3],
                )
            )
            selected_ids.add(ai_item.productId)
            if len(selected) == self.MAX_RECOMMEND_COUNT:
                break

        for fallback in fallback_items:
            if len(selected) == self.MAX_RECOMMEND_COUNT:
                break
            if fallback.product.id not in selected_ids:
                selected.append(fallback)
                selected_ids.add(fallback.product.id)

        return selected, "AI" if selected else "RULE_BASED"


recommend_service = RecommendService()
