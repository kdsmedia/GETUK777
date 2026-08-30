package domain.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class CasinoGameProxyTest : FunSpec({

    test("carries the provider host as one label and leaves the path and query alone") {
        CasinoGameProxy.proxify(
            "https://api-ire1.214adera.shop/api/brands/launcher/4a0ba06a?x=1&y=2"
        ) shouldBe "https://api--ire1-214adera-shop.djmgame.com/api/brands/launcher/4a0ba06a?x=1&y=2"
    }

    test("a literal dash is doubled so the host survives the round trip") {
        CasinoGameProxy.encode("my-cdn.x.io") shouldBe "my--cdn-x-io"
        CasinoGameProxy.encode("www.google.com") shouldBe "www-google-com"
    }

    test("upgrades http to https — the proxy answers on TLS only") {
        CasinoGameProxy.proxify("http://games.provider.com/play") shouldBe
            "https://games-provider-com.djmgame.com/play"
    }

    test("a URL already on our domain is left as it is") {
        val ours = "https://games-provider-com.djmgame.com/play"
        CasinoGameProxy.proxify(ours) shouldBe ours
    }

    test("leaves alone what it cannot carry") {
        CasinoGameProxy.proxify("not a url") shouldBe "not a url"
        CasinoGameProxy.proxify("https://localhost/play") shouldBe "https://localhost/play"
    }
})
