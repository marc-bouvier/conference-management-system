package cms.domain.conference

import cms.domain.{InMemoryEventPublisher, InMemoryEventSourcedRepository}
import org.scalatest.{FlatSpec, Matchers}

class ConferenceTest extends FlatSpec with Matchers {

  trait Setup {
    val eventPublisher = new InMemoryEventPublisher
    val conferenceEventRepository = new InMemoryEventSourcedRepository(eventPublisher)
    val conferenceCommandHandler = new ConferenceCommandHandler(conferenceEventRepository)
  }

  "A conference" can "be created" in new Setup {

    // When
    conferenceCommandHandler.handle(CreateConference(name = "MixIT 2018", slug = "mix-it-18"))

    // Then
    conferenceEventRepository.getEventStream("mix-it-18") should contain only
      ConferenceCreated(name = "MixIT 2018", slug = "mix-it-18")
  }

  it can "be published" in new Setup {

    // Given
    val conferenceId = "mix-it-18"
    val history = Seq(ConferenceCreated(name = "MixIT 2018", slug = conferenceId))

    conferenceEventRepository.setHistory(conferenceId, history: _*)

    // When
    conferenceCommandHandler handle PublishConference(id = "mix-it-18")

    // Then
    conferenceEventRepository.getEventStream(conferenceId) should contain theSameElementsInOrderAs
      history :+ ConferencePublished(id = "mix-it-18")
  }

  it can "be updated with a name" in new Setup {

    // Given
    val conferenceId = "mix-it-18"
    val history = Seq(ConferenceCreated(name = "MixIT 2018", slug = conferenceId))

    conferenceEventRepository.setHistory(conferenceId, history: _*)

    // When
    conferenceCommandHandler.handle(UpdateConference(conferenceId, name = "MixIT 18"))

    // Then
    conferenceEventRepository.getEventStream(conferenceId) should contain theSameElementsInOrderAs
      history :+ ConferenceUpdated(id = conferenceId, name = "MixIT 18")
  }

  it can "not be created if its the slug is already in use" in new Setup {

    // Given
    val conferenceId = "mix-it-18"
    val history = ConferenceCreated(name = "MixIT 2018", slug = conferenceId)

    conferenceEventRepository.setHistory(conferenceId, history)

    // When
    conferenceCommandHandler.handle(CreateConference(name = "MixIT 18", slug = conferenceId))

    // Then
    conferenceEventRepository.getEventStream(conferenceId) should contain only history
  }

  it can "not be published if not created before" in new Setup {

    // When
    conferenceCommandHandler handle PublishConference(id = "mix-it-18")

    // Then
    conferenceEventRepository.find[Conference]("mix-it-18") shouldBe None
  }

  it can "not be updated if not created before" in new Setup {

    // When
    conferenceCommandHandler.handle(UpdateConference("mix-it-18", name = "MixIT 18"))

    // Then
    conferenceEventRepository.find[Conference]("mix-it-18") shouldBe None
  }

  it should "not be re-published if already published" in new Setup {

    // Given
    val conferenceId = "mix-it-18"
    val history = Seq(
      ConferenceCreated(name = "MixIT 2018", slug = conferenceId),
      ConferencePublished(id = conferenceId)
    )

    conferenceEventRepository.setHistory(conferenceId, history: _*)

    // When
    conferenceCommandHandler handle PublishConference(id = conferenceId)

    // Then
    conferenceEventRepository.getEventStream(conferenceId) should contain theSameElementsInOrderAs history
  }

  it should "reserve all the requested seats given a reservation for a seat type with sufficient quota" in new Setup {

    // Given
    val history = Seq(
      ConferenceCreated(name = "MixIT", slug = "mix-it-18"),
      SeatsAdded(conferenceId = "mix-it-18", seatType = "Workshop", quota = 10),
      ConferencePublished(id = "mix-it-18")
    )
    conferenceEventRepository.setHistory("mix-it-18", history: _*)

    // When
    conferenceCommandHandler handle MakeSeatsReservation(
      orderId = "ID-1",
      conferenceId = "mix-it-18",
      request = "Workshop" -> 5
    )

    // Then
    conferenceEventRepository.getEventStream("mix-it-18") should contain theSameElementsInOrderAs
      history :+ SeatsReserved(conferenceId = "mix-it-18", orderId = "ID-1", seats = "Workshop" -> 5)
  }

  it should "reject a seats reservation for a conference that is not published yet" in new Setup {

    // Given
    val history = Seq(
      ConferenceCreated(name = "MixIT 2018", slug = "mix-it-18"),
      SeatsAdded(conferenceId = "mix-it-18", seatType = "Workshop", quota = 10)
    )
    conferenceEventRepository.setHistory("mix-it-18", history: _*)

    // When
    conferenceCommandHandler handle MakeSeatsReservation(
      orderId = "ID-1",
      conferenceId = "mix-it-18",
      request = "Workshop" -> 3
    )

    // Then
    conferenceEventRepository.getEventStream("mix-it-18") should contain theSameElementsInOrderAs
      history :+ SeatsReservationRejected(conferenceId = "mix-it-18", orderId = "ID-1", request = "Workshop" -> 3)
  }

  it should "reject a seats reservation for a seat type with an insufficient quota" in new Setup {

    // Given
    val history = Seq(
      ConferenceCreated(name = "MixIT", slug = "mix-it-18"),
      SeatsAdded(conferenceId = "mix-it-18", seatType = "Workshop", quota = 10),
      ConferencePublished(id = "mix-it-18"),
      SeatsReserved(conferenceId = "mix-it-18", orderId = "ID-1", seats = "Workshop" -> 7)
    )

    conferenceEventRepository.setHistory("mix-it-18", history: _*)

    // When
    conferenceCommandHandler handle MakeSeatsReservation(
      orderId = "ID-2",
      conferenceId = "mix-it-18",
      request = "Workshop" -> 4
    )

    // Then
    conferenceEventRepository.getEventStream("mix-it-18") should contain theSameElementsInOrderAs
      history :+ SeatsReservationRejected(conferenceId = "mix-it-18", orderId = "ID-2", request = "Workshop" -> 4)
  }

  it should "reject a seats reservation for a missing seat type" in new Setup {

    // Given
    val history = Seq(
      ConferenceCreated(name = "MixIT 2018", slug = "mix-it-18"),
      ConferencePublished(id = "mix-it-18")
    )

    conferenceEventRepository.setHistory("mix-it-18", history: _*)

    // When
    conferenceCommandHandler handle MakeSeatsReservation(
      orderId = "ID-1",
      conferenceId = "mix-it-18",
      request = "Conference" -> 3
    )

    // Then
    conferenceEventRepository.getEventStream("mix-it-18") should contain theSameElementsInOrderAs
      history :+ SeatsReservationRejected(conferenceId = "mix-it-18", orderId = "ID-1", request = "Conference" -> 3)
  }

  it should "discard a seats reservation if not created before" in new Setup {

    // When
    conferenceCommandHandler handle MakeSeatsReservation(
      orderId = "ID-1",
      conferenceId = "mix-it-18",
      request = "Workshop" -> 3
    )

    // Then
    conferenceEventRepository.find[Conference]("mix-it-18") shouldBe None
  }

  it should "use its slug as identifier" in {

    // When
    val conference = Conference(name = "MixIT 2018", slug = "mix-it-18")

    // Then
    conference.id should equal("mix-it-18")
  }

  it can "not be instantiated from an empty history" in {
    the[IllegalArgumentException] thrownBy {
      Conference(id = "mix-it-18", Nil)
    } should have message "Either create a new conference from a slug or provide an history"
  }

  "A seat type" can "be added to an existing conference with an initial quota" in new Setup {

    // Given
    val conferenceId = "mix-it-18"
    val history = Seq(ConferenceCreated(name = "MixIT 2018", slug = conferenceId))

    conferenceEventRepository.setHistory(conferenceId, history: _*)

    // When
    conferenceCommandHandler handle AddSeatsToConference(conferenceId, seatType = "Workshop", quota = 100)

    // Then
    conferenceEventRepository.getEventStream(conferenceId) should contain theSameElementsInOrderAs
      history :+ SeatsAdded(conferenceId, seatType = "Workshop", quota = 100)
  }

  it can "not be added if the conference has not been created before" in new Setup {

    // When
    conferenceCommandHandler handle AddSeatsToConference("mix-it-18", seatType = "Workshop", quota = 100)

    // Then
    conferenceEventRepository.find[Conference]("mix-it-18") shouldBe None
  }

  it can "not be added if the seat type already exists" in new Setup {

    // Given
    val conferenceId = "mix-it-18"
    val history = Seq(
      ConferenceCreated(name = "MixIT 2018", slug = conferenceId),
      SeatsAdded(conferenceId, seatType = "Workshop", quota = 100)
    )

    conferenceEventRepository.setHistory(conferenceId, history: _*)

    // When
    conferenceCommandHandler handle AddSeatsToConference(conferenceId, seatType = "Workshop", quota = 50)

    // Then
    conferenceEventRepository.getEventStream(conferenceId) should contain theSameElementsInOrderAs history
  }
}
