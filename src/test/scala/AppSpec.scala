package me.amanj.greenish

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import models._
import java.time.ZoneId

class AppSpec() extends Matchers
  with AnyWordSpecLike {
  "getPrometheusIds" must {
    "work when there are duplicate IDs" in {
      val config = new AppConfig(
        Seq(
          Group(0, "Group1", Seq(
              Job(0, "Job1", "job_1", "/tmp/first_script",
                "yyyy-MM-dd-HH", Hourly, 3,
                ZoneId.of("UTC"), 24,
                AlertLevels(0, 1, 2, 3)),
              Job(1, "Job2", "job_1", "/tmp/second_script job2",
                "yyyy-MM-dd-HH", Daily, 2,
                ZoneId.of("UTC"), 24,
                AlertLevels(0, 1, 2, 3)),
            )),
          Group(1, "Group2", Seq(
            Job(0, "Job3", "job_2", "/tmp/third_script",
                "yyyy-MM-dd", Monthly, 1,
                ZoneId.of("UTC"), 3,
                AlertLevels(0, 1, 2, 3)),
            Job(1, "Job4", "job_2", "/tmp/fourth_script",
                "yyyy-01-01", Annually, 1,
                ZoneId.of("UTC"), 3,
                AlertLevels(0, 1, 2, 3)),
            )),
        ),
        30,
        "127.0.0.1",
        8080,
        Seq("VAR1" -> "foo", "VAR2" -> "bar"),
      )

      val expected = Set("job_1", "job_2")

      val actual = App.getPrometheusIds(config)

      actual shouldBe expected
    }

    "work when there are no duplicate IDs" in {
      val config = new AppConfig(
        Seq(
          Group(0, "Group1", Seq(
              Job(0, "Job1", "job_1", "/tmp/first_script",
                "yyyy-MM-dd-HH", Hourly, 3,
                ZoneId.of("UTC"), 24,
                AlertLevels(0, 1, 2, 3)),
              Job(1, "Job2", "job_2", "/tmp/second_script job2",
                "yyyy-MM-dd-HH", Daily, 2,
                ZoneId.of("UTC"), 24,
                AlertLevels(0, 1, 2, 3)),
            )),
          Group(1, "Group2", Seq(
            Job(0, "Job3", "job_3", "/tmp/third_script",
                "yyyy-MM-dd", Monthly, 1,
                ZoneId.of("UTC"), 3,
                AlertLevels(0, 1, 2, 3)),
            Job(1, "Job4", "job_4", "/tmp/fourth_script",
                "yyyy-01-01", Annually, 1,
                ZoneId.of("UTC"), 3,
                AlertLevels(0, 1, 2, 3)),
            )),
        ),
        30,
        "127.0.0.1",
        8080,
        Seq("VAR1" -> "foo", "VAR2" -> "bar"),
      )

      val expected = Set("job_1", "job_2", "job_3", "job_4")

      val actual = App.getPrometheusIds(config)

      actual shouldBe expected
    }
  }
}
