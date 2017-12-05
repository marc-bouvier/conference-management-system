package cms.domain.order.projections

import cms.domain.Projection

case class PlacedOrderProjection(
  conferenceId: String,
  id: String,
  lastUpdate: Long,
  status: String,
  requestedSeats: (String, Int)
) extends Projection
