package me.amanj.greenish.endpoints

import akka.actor.{ActorRef, Props}
import akka.http.scaladsl.model.{StatusCodes, ContentTypes}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.concurrent.Eventually
import java.time.{ZoneId, ZonedDateTime, Instant}
import scala.concurrent.duration._
import scala.language.postfixOps
import me.amanj.greenish.models._
import me.amanj.greenish.stats._
import me.amanj.greenish.checker._
import io.circe.parser._
import java.io.File

class RoutesSpec()
    extends AnyWordSpecLike
    with Matchers
    with ScalatestRouteTest
    with BeforeAndAfterAll
    with Eventually{

  val dir1 = new File("/tmp/2020-06-25-14")
  val tstamp = 1000L
  implicit val patience: PatienceConfig = PatienceConfig(15 seconds, 1 second)
  override def beforeAll: Unit = {
    dir1.mkdirs
    stats = system.actorOf(
      Props(new StatsCollector(Set("p1", "p2", "p3"))))
    checker = system.actorOf(
      Props(new StatusChecker(Seq(group1, group2), stats, Seq.empty,
        () => tstamp)))
    val time = ZonedDateTime.parse("2020-06-25T15:05:30+01:00[UTC]")
    checker ! Refresh(() => time)
    routes = new Routes(checker, stats, 10, () => time)
  }
  override def afterAll: Unit = {
    dir1.delete
    cleanUp()
  }

  val lsScript = getClass.getResource("/test-ls").getFile
  val lsEnvScript = getClass.getResource("/test-ls-env").getFile

  val job1 = Job(0, "job1", "p1", s"$lsScript /tmp",
    "yyyy-MM-dd-HH", Hourly, 1, ZoneId.of("UTC"),
    2, AlertLevels(0, 1, 2, 3),
  )

  val job2 = Job(1, "job2", "p2", s"$lsScript /tmp",
    "yyyy-MM-dd-HH", Hourly, 1, ZoneId.of("UTC"),
    2, AlertLevels(0, 1, 2, 3),
  )

  val job3 = Job(0, "job3", "p3", s"$lsScript /tmp",
    "yyyy-MM-dd-HH", Hourly, 1, ZoneId.of("UTC"),
    2, AlertLevels(0, 1, 2, 3),
  )

  val group1 = Group(0, "group1", Seq(job1, job2))
  val group2 = Group(1, "group2", Seq(job3))

  var checker: ActorRef = _
  var stats: ActorRef = _
  var routes: Routes = _

  "isHealthy" must {
    "return false when group summary is empty" in {
      Routes.isHealthy(Seq.empty, Long.MaxValue) shouldBe false
    }

    "return false when no jobs has returned their peirods healths" in {
      val groups = Seq(
        GroupStatus(group1, Array(
          JobStatus(job1, tstamp, Seq.empty),
          JobStatus(job2, tstamp, Seq.empty))
        ),
        GroupStatus(group2, Array(
          JobStatus(job3, tstamp, Seq.empty))
        ),
      )
      Routes.isHealthy(groups, Long.MaxValue) shouldBe false
    }

    "return true when some jobs have returned their peirods healths" in {
      val groups = Seq(
        GroupStatus(group1, Array(
          JobStatus(job1, tstamp, Seq(PeriodHealth("2020-06-25-13", false))),
          JobStatus(job2, tstamp, Seq.empty))
        ),
        GroupStatus(group2, Array(
          JobStatus(job3, tstamp, Seq.empty))
        ),
      )
      Routes.isHealthy(groups, Long.MaxValue) shouldBe true
    }

    "return true when all jobs have returned their peirods healths" in {
      val groups = Seq(
        GroupStatus(group1, Array(
          JobStatus(job1, tstamp, Seq(
            PeriodHealth("2020-06-25-13", false),
            PeriodHealth("2020-06-25-14", true),
            )),
          JobStatus(job2, tstamp, Seq(
            PeriodHealth("2020-06-25-13", false),
            PeriodHealth("2020-06-25-14", true),
            )),
        )),
        GroupStatus(group2, Array(
          JobStatus(job3, tstamp, Seq(
            PeriodHealth("2020-06-25-13", false),
            PeriodHealth("2020-06-25-14", true),
            )),
        )),
      )
      Routes.isHealthy(groups, Long.MaxValue) shouldBe true
    }

    "return false when all jobs have returned their peirods healths, but not recently" in {
      val groups = Seq(
        GroupStatus(group1, Array(
          JobStatus(job1, tstamp, Seq(
            PeriodHealth("2020-06-25-13", false),
            PeriodHealth("2020-06-25-14", true),
            )),
          JobStatus(job2, tstamp, Seq(
            PeriodHealth("2020-06-25-13", false),
            PeriodHealth("2020-06-25-14", true),
            )),
        )),
        GroupStatus(group2, Array(
          JobStatus(job3, tstamp, Seq(
            PeriodHealth("2020-06-25-13", false),
            PeriodHealth("2020-06-25-14", true),
            )),
        )),
      )
      Routes.isHealthy(groups, 1) shouldBe false
    }
  }

  "Routes" must {
    "properly handle GET/maxlag request" in {
      eventually {
        Get("/maxlag") ~> routes.routes ~> check {
          val actual = parse(responseAs[String])
            .flatMap(_.as[Lag]).getOrElse(null)
          val expected = Lag(1)
          actual shouldBe expected
        }
      }
    }

    "properly handle GET/state request" in {
      eventually {
        Get("/state") ~> routes.routes ~> check {
          val actual = parse(responseAs[String])
            .flatMap(_.as[Seq[GroupStatus]]).getOrElse(null)
          val expected = Seq(
            GroupStatus(group1, Array(
              JobStatus(job1, tstamp, Seq(
                PeriodHealth("2020-06-25-13", false),
                PeriodHealth("2020-06-25-14", true),
                )),
              JobStatus(job2, tstamp, Seq(
                PeriodHealth("2020-06-25-13", false),
                PeriodHealth("2020-06-25-14", true),
                )),
            )),
            GroupStatus(group2, Array(
              JobStatus(job3, tstamp, Seq(
                PeriodHealth("2020-06-25-13", false),
                PeriodHealth("2020-06-25-14", true),
                )),
            )),
          )
          actual shouldBe expected
        }
      }
    }

    "properly handle GET/missing request" in {
      eventually {
        Get("/missing") ~> routes.routes ~> check {
          val actual = parse(responseAs[String])
            .flatMap(_.as[Seq[GroupStatus]]).getOrElse(null)
          val expected = Seq(
            GroupStatus(group1, Array(
              JobStatus(job1, tstamp, Seq(
                PeriodHealth("2020-06-25-13", false),
                )),
              JobStatus(job2, tstamp, Seq(
                PeriodHealth("2020-06-25-13", false),
                ))
            )),
            GroupStatus(group2, Array(
              JobStatus(job3, tstamp, Seq(
                PeriodHealth("2020-06-25-13", false),
                )),
            )),
          )
          actual shouldBe expected
        }
      }
    }

    "properly handle GET/summary request" in {
      eventually {
        Get("/summary") ~> routes.routes ~> check {
          val actual = parse(responseAs[String])
            .flatMap(_.as[Seq[GroupStatusSummary]]).getOrElse(null)
          val expected = Seq(
            GroupStatusSummary(0, group1.name, Seq(
              JobStatusSummary(0, job1.name, 1, Normal),
              JobStatusSummary(1, job2.name, 1, Normal),
              )),
            GroupStatusSummary(1, group2.name, Seq(
              JobStatusSummary(0, job3.name, 1, Normal),
              )),
            )
          actual shouldBe expected
        }
      }
    }

    "properly handle GET/group/gid request when id exists" in {
      eventually {
        Get("/group/0") ~> routes.routes ~> check {
          val actual = parse(responseAs[String])
            .flatMap(_.as[GroupStatus]).getOrElse(null)
          val expected = GroupStatus(group1, Array(
            JobStatus(job1, tstamp, Seq(
                PeriodHealth("2020-06-25-13", false),
                PeriodHealth("2020-06-25-14", true),
                )),
            JobStatus(job2, tstamp, Seq(
                PeriodHealth("2020-06-25-13", false),
                PeriodHealth("2020-06-25-14", true),
                )),
            ))
          actual shouldBe expected
        }
      }
    }

    "properly handle GET/group/gid request when gid does not exist" in {
      eventually {
        Get("/group/10") ~> routes.routes ~> check {
          status shouldBe StatusCodes.BadRequest
        }
      }
    }

    "properly handle GET/group/gid/job/jid request when both gid and jid exist" in {
      eventually {
        Get("/group/0/job/0") ~> routes.routes ~> check {
          val actual = parse(responseAs[String])
            .flatMap(_.as[JobStatus]).getOrElse(null)
          val expected = JobStatus(job1, tstamp, Seq(
            PeriodHealth("2020-06-25-13", false),
            PeriodHealth("2020-06-25-14", true),
            ))
          actual shouldBe expected
        }
      }
    }

    "properly handle GET/group/gid/job/jid request when gid does not exist" in {
      eventually {
        Get("/group/10/job/9") ~> routes.routes ~> check {
          status shouldBe StatusCodes.BadRequest
        }
      }
    }

    "properly handle GET/group/gid/job/jid request when jid does not exist" in {
      eventually {
        Get("/group/0/job/9") ~> routes.routes ~> check {
          status shouldBe StatusCodes.BadRequest
        }
      }
    }

    "properly handle GET/state/refresh" in {
      val checker = system.actorOf(Props(
        new StatusChecker(Seq(group1, group2), stats,
          Seq.empty, () => tstamp)))
      val time = ZonedDateTime.parse("2020-06-25T15:05:30+01:00[UTC]")
      val routes = new Routes(checker, stats, 10 * 1000 * 5, () => time)

      Get("/state/refresh") ~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
      }

      eventually {
        Get("/state") ~> routes.routes ~> check {
          val actual = parse(responseAs[String])
            .flatMap(_.as[Seq[GroupStatus]]).getOrElse(null)
          val expected = Seq(
            GroupStatus(group1, Array(
              JobStatus(job1, tstamp, Seq(
                PeriodHealth("2020-06-25-13", false),
                PeriodHealth("2020-06-25-14", true),
                )),
              JobStatus(job2, tstamp, Seq(
                PeriodHealth("2020-06-25-13", false),
                PeriodHealth("2020-06-25-14", true),
                )),
            )),
            GroupStatus(group2, Array(
              JobStatus(job3, 1000, Seq(
                PeriodHealth("2020-06-25-13", false),
                PeriodHealth("2020-06-25-14", true),
              ))
            )),
          )
          actual shouldBe expected
        }
      }
    }

    "properly handle GET/group/gid/refresh request when id exists" in {
      val checker = system.actorOf(Props(
        new StatusChecker(Seq(group1, group2), stats,
          Seq.empty, () => tstamp)))
      val time = ZonedDateTime.parse("2020-06-25T15:05:30+01:00[UTC]")
      val routes = new Routes(checker, stats, 10 * 1000 * 5, () => time)

      Get("/group/0/refresh") ~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
      }

      eventually {
        Get("/state") ~> routes.routes ~> check {
          val actual = parse(responseAs[String])
            .flatMap(_.as[Seq[GroupStatus]]).getOrElse(null)
          val expected = Seq(
            GroupStatus(group1, Array(
              JobStatus(job1, tstamp, Seq(
                PeriodHealth("2020-06-25-13", false),
                PeriodHealth("2020-06-25-14", true),
                )),
              JobStatus(job2, tstamp, Seq(
                PeriodHealth("2020-06-25-13", false),
                PeriodHealth("2020-06-25-14", true),
                )),
            )),
            GroupStatus(group2, Array(
              JobStatus(job3, -1, Seq.empty)
            )),
          )
          actual shouldBe expected
        }
      }
    }

    "properly handle GET/group/gid/refresh request when gid does not" in {
      eventually {
        Get("/group/10/refresh") ~> routes.routes ~> check {
          status shouldBe StatusCodes.BadRequest
        }
      }
    }

    "properly handle GET/group/gid/job/jid/refresh request when both gid and jid exist" in {
      val checker = system.actorOf(
        Props(new StatusChecker(Seq(group1, group2), stats, Seq.empty,
        () => tstamp)))
      val time = ZonedDateTime.parse("2020-06-25T15:05:30+01:00[UTC]")
      val routes = new Routes(checker, stats, 1000 * 10 * 5, () => time)

      Get("/group/0/job/0/refresh") ~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
      }

      eventually {
        Get("/state") ~> routes.routes ~> check {
          val actual = parse(responseAs[String])
            .flatMap(_.as[Seq[GroupStatus]]).getOrElse(null)
          val expected = Seq(
            GroupStatus(group1, Array(
              JobStatus(job1, tstamp, Seq(
                PeriodHealth("2020-06-25-13", false),
                PeriodHealth("2020-06-25-14", true),
                )),
              JobStatus(job2, -1, Seq.empty)
            )),
            GroupStatus(group2, Array(
              JobStatus(job3, -1, Seq.empty)
            )),
          )
          actual shouldBe expected
        }
      }
    }

    "properly handle GET/group/gid/job/jid/refresh request when gid does not exist" in {
      eventually {
        Get("/group/10/job/9/refresh") ~> routes.routes ~> check {
          status shouldBe StatusCodes.BadRequest
        }
      }
    }

    "properly handle GET/group/gid/job/jid/refresh request when jid does not exist" in {
      eventually {
        Get("/group/0/job/9/refresh") ~> routes.routes ~> check {
          status shouldBe StatusCodes.BadRequest
        }
      }
    }

    "properly handle GET/dashboard request" in {
      Get("/dashboard") ~> routes.routes ~> check {
        status shouldBe StatusCodes.TemporaryRedirect
        contentType shouldBe ContentTypes.`text/html(UTF-8)`
      }
    }

    "properly handle GET/dashboard/ request" in {
      Get("/dashboard/") ~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        contentType shouldBe ContentTypes.`text/html(UTF-8)`
      }
    }

    "properly handle GET/dashboard/index.html request" in {
      Get("/dashboard/index.html") ~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        contentType shouldBe ContentTypes.`text/html(UTF-8)`
      }
    }

    "properly handle GET/system request" in {
      Get("/system") ~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
      }
    }

    "properly handle GET/prometheus request" in {
      Get("/prometheus") ~> routes.routes ~> check {
        contentType.toString.contains("text/plain") shouldBe true
        contentType.toString.contains("version=0.0.4") shouldBe true
        status shouldBe StatusCodes.OK
        val actual = responseAs[String]
        actual.contains("greenish") shouldBe true
        actual.contains("# TYPE") shouldBe true
        actual.contains("# HELP") shouldBe true
      }
    }

    "send good health when hitting GET/health after successful refresh, then if no further refresh happens, goes to unhealthy again" in {
      val checker = system.actorOf(
        Props(new StatusChecker(Seq(group1, group2), stats)))
      val nowFun = () => ZonedDateTime.ofInstant(Instant.now(),
        ZoneId.systemDefault())
      checker ! Refresh(nowFun)
      routes = new Routes(checker, stats, 1000 * 1 * 2)

      eventually {
        Get("/health") ~> routes.routes ~> check {
          val actual = parse(responseAs[String])
            .getOrElse(null)
          val expected = healthJson(true)
          actual shouldBe expected
        }
      }

      // And the status will eventually decays
      eventually {
        Get("/health") ~> routes.routes ~> check {
          val actual = parse(responseAs[String])
            .getOrElse(null)
          val expected = healthJson(false)
          actual shouldBe expected
        }
      }
    }

    "send bad health when hitting GET/health befor successful refresh" in {
      checker = system.actorOf(
        Props(new StatusChecker(Seq(group1, group2), stats, Seq.empty,
          () => System.currentTimeMillis)))
      val time = ZonedDateTime.parse("2020-06-25T15:05:30+01:00[UTC]")
      routes = new Routes(checker, stats, 1000 * 10 * 5, () => time)

      Get("/health") ~> routes.routes ~> check {
        val actual = parse(responseAs[String])
          .getOrElse(null)
        val expected = healthJson(false)
        actual shouldBe expected
      }
    }
  }
}
