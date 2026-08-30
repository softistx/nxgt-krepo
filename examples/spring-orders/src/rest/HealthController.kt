package com.strange.example.orders.rest

import com.strange.example.orders.api.apis.IHealthService
import com.strange.example.orders.api.models.Health
import com.strange.example.orders.api.models.HealthResponse
import org.springframework.web.bind.annotation.RestController

/**
 * `GET /health`, and the reason there is no `HealthService` beside it.
 *
 * The layering is repository → service → controller because a rule and a query belong in different
 * files; this endpoint has neither. A service whose whole body is a constant would be a package to
 * step through rather than a seam, so the controller answers. The moment health means anything —
 * a ping to Mongo, a migration check — it gains one.
 *
 * A second tag in the document (`health-controller`) and therefore a second generated interface:
 * `groupBy: Tag` is what decides how many there are.
 */
@RestController
class HealthController : IHealthService {
    override suspend fun health(): HealthResponse = HealthResponse(data = Health(status = "UP"))
}
