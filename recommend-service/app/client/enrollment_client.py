import httpx
import logging
from app.config.settings import settings
from app.model.schemas import EnrollmentHistoryResponse

logger = logging.getLogger(__name__)


class EnrollmentServiceClient:
    """
    Enrollment Service REST 클라이언트
    - 가맹점의 입고 완료 상품 ID 목록 조회
    - 이전 실습 이미지와의 호환을 위해 구형 수강 이력 API도 fallback으로 지원
    """

    def __init__(self):
        self.base_url = settings.enrollment_service_url

    async def get_enrollment_history(self, user_id: int) -> EnrollmentHistoryResponse:
        """
        GET /enrollments/internal/store/{storeId}/received
        가맹점이 입고 완료한 상품 ID 목록 조회
        """
        received_url = (
            f"{self.base_url}/api/enrollments/internal/store/{user_id}/received"
        )
        try:
            async with httpx.AsyncClient(timeout=5.0) as client:
                response = await client.get(received_url)
                response.raise_for_status()
                data = response.json()
                return EnrollmentHistoryResponse(
                    userId=data.get("userId", user_id),
                    receivedProductIds=data.get("receivedProductIds", []),
                )
        except httpx.HTTPError as e:
            logger.warning(
                "[EnrollmentClient] 입고 이력 API 조회 실패, 구형 API로 재시도 - "
                f"storeId: {user_id}, error: {e}"
            )

        legacy_url = f"{self.base_url}/api/enrollments/internal/history/{user_id}"
        try:
            async with httpx.AsyncClient(timeout=5.0) as client:
                response = await client.get(legacy_url)
                response.raise_for_status()
                data = response.json()
                return EnrollmentHistoryResponse(
                    userId=data.get("userId", user_id),
                    receivedProductIds=data.get("activeCourseIds", []),
                )
        except httpx.HTTPError as e:
            logger.error(
                f"[EnrollmentClient] 발주 이력 조회 실패 - storeId: {user_id}, error: {e}"
            )
            return EnrollmentHistoryResponse(userId=user_id, receivedProductIds=[])


enrollment_client = EnrollmentServiceClient()
