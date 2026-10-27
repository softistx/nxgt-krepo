package com.softistx.material.school

import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import java.io.File

/**
 * The evaluation bench every visual slice re-runs. A no-op unless `SCHOOL_BENCH_DIR` is set; then it
 * writes every shot, `manifest.json` and one contact sheet per viewport there. `SCHOOL_BENCH_FILTER`
 * (comma-separated parts of a file name, such as `home,tablet-dark`) narrows the shots. See md-school/README.md.
 */
class SchoolBenchTest :
    FeatureSpec({
        feature("the bench shot list") {
            scenario("covers three viewports, two themes, six screens and two detail views") {
                benchShots().size shouldBe Viewport.entries.size * 2 * 8
                benchShots().map { it.file }.distinct().size shouldBe benchShots().size
            }
        }

        feature("the bench") {
            scenario("writes the shots, manifest and contact sheets when SCHOOL_BENCH_DIR is set") {
                val dir = System.getenv("SCHOOL_BENCH_DIR") ?: return@scenario
                val shots = benchShots(System.getenv("SCHOOL_BENCH_FILTER"))
                writeBench(File(dir), shots)
                File(dir, "manifest.json").exists() shouldBe true
                shots.all { File(dir, it.file).length() > 0 } shouldBe true
            }
        }
    })
