package org.jellyfin.androidtv.ui.playback.pip

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class PiPManagerTests : FunSpec({
	test("clampAspectRatio passes through 16:9 video unchanged") {
		PiPManager.clampAspectRatio(1920, 1080) shouldBe (1920 to 1080)
	}

	test("clampAspectRatio passes through 4:3 video unchanged") {
		PiPManager.clampAspectRatio(640, 480) shouldBe (640 to 480)
	}

	test("clampAspectRatio passes through 1:1 square video unchanged") {
		PiPManager.clampAspectRatio(1080, 1080) shouldBe (1080 to 1080)
	}

	test("clampAspectRatio passes through 21:9 ultrawide within bounds") {
		// 2560/1080 = 2.37, within the 2.39 limit
		PiPManager.clampAspectRatio(2560, 1080) shouldBe (2560 to 1080)
	}

	test("clampAspectRatio clamps ultra-wide ratios above 2.39:1") {
		// 3:1 is wider than 2.39:1
		PiPManager.clampAspectRatio(3000, 1000) shouldBe (239 to 100)
	}

	test("clampAspectRatio clamps extreme ultra-wide ratio") {
		// 10:1
		PiPManager.clampAspectRatio(10000, 1000) shouldBe (239 to 100)
	}

	test("clampAspectRatio clamps ultra-tall ratios below 1:2.39") {
		// 1:3 is taller than 1:2.39
		PiPManager.clampAspectRatio(1000, 3000) shouldBe (100 to 239)
	}

	test("clampAspectRatio clamps extreme ultra-tall ratio") {
		// 1:10
		PiPManager.clampAspectRatio(1000, 10000) shouldBe (100 to 239)
	}

	test("clampAspectRatio ignores zero width") {
		PiPManager.clampAspectRatio(0, 1080) shouldBe (0 to 1080)
	}

	test("clampAspectRatio ignores zero height") {
		PiPManager.clampAspectRatio(1920, 0) shouldBe (1920 to 0)
	}

	test("clampAspectRatio ignores negative width") {
		PiPManager.clampAspectRatio(-1920, 1080) shouldBe (-1920 to 1080)
	}

	test("clampAspectRatio ignores negative height") {
		PiPManager.clampAspectRatio(1920, -1080) shouldBe (1920 to -1080)
	}

	test("clampAspectRatio handles boundary ratio exactly at 2.39") {
		// 239:100 = exactly 2.39
		PiPManager.clampAspectRatio(239, 100) shouldBe (239 to 100)
	}

	test("clampAspectRatio handles ratio just above 2.39") {
		// 240:100 = 2.40, just over limit
		PiPManager.clampAspectRatio(240, 100) shouldBe (239 to 100)
	}

	test("clampAspectRatio handles boundary ratio exactly at 1:2.39") {
		// 100:239 = 1/2.39
		PiPManager.clampAspectRatio(100, 239) shouldBe (100 to 239)
	}

	test("clampAspectRatio handles ratio just below 1:2.39") {
		// 100:240 = 0.4167, just under 1/2.39 = 0.4184
		PiPManager.clampAspectRatio(100, 240) shouldBe (100 to 239)
	}
})
