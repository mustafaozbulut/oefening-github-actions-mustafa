package be.kunlabora.crafters.kunlaquota.web.ui

import be.kunlabora.crafters.kunlaquota.Failure
import be.kunlabora.crafters.kunlaquota.service.*
import be.kunlabora.crafters.kunlaquota.service.domain.Quote
import be.kunlabora.crafters.kunlaquota.service.domain.QuoteId
import be.kunlabora.crafters.kunlaquota.service.domain.QuoteShare
import be.kunlabora.crafters.kunlaquota.web.ui.components.Hero.hero
import be.kunlabora.crafters.kunlaquota.web.ui.screens.AddQuoteModal.addQuoteLine
import be.kunlabora.crafters.kunlaquota.web.ui.screens.AddQuoteModal.addQuoteModal
import be.kunlabora.crafters.kunlaquota.web.ui.screens.ShareQuoteModal.shareQuoteModal
import be.kunlabora.crafters.kunlaquota.web.ui.screens.ShowQuotesScreen.errorMessage
import be.kunlabora.crafters.kunlaquota.web.ui.screens.ShowQuotesScreen.showQuotes
import kotlinx.html.*
import kotlinx.html.stream.appendHTML
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.servlet.function.RequestPredicates
import org.springframework.web.servlet.function.RouterFunctionDsl
import org.springframework.web.servlet.function.ServerResponse
import org.springframework.web.servlet.function.paramOrNull
import java.io.StringWriter

const val baseUiUrl = "/ui"

fun uiRoutes(quotes: IQuotes): RouterFunctionDsl.() -> Unit = {
    GET(RequestPredicates.param("share") { it.isNotBlank() }) { request ->
        val quoteShare = QuoteShare(request.paramOrNull("share")!!)
        quotes.findByQuoteShare(quoteShare, SurroundingQuotesSize(3))
            .map { quotes ->
                ServerResponse.status(HttpStatus.OK)
                    .contentType(MediaType.TEXT_HTML)
                    .body(wrapper("Shared quote: ${quoteShare.value}") {
                        showQuotes(quotes)
                    })
            }.andHandleFailure()
    }


    GET("") {
        val title = "KunlaQuota"
        ServerResponse.status(HttpStatus.OK)
            .contentType(MediaType.TEXT_HTML)
            .body(
                wrapper(title) {
                    quotes.findAll()
                        .map { quotes -> showQuotes(quotes) }
                }
            )
    }
    GET("showModal") {
        ServerResponse.status(HttpStatus.OK)
            .contentType(MediaType.TEXT_HTML)
            .body(
                partial {
                    addQuoteModal()
                }
            )
    }
    POST("new/addLine") {
        ServerResponse.status(HttpStatus.OK)
            .contentType(MediaType.TEXT_HTML)
            .body(
                partial { addQuoteLine() }
            )
    }
    POST("new") { request ->
        val names = request.params()["nameInput"] ?: emptyList()
        val texts = request.params()["textInput"] ?: emptyList()

        val addQuote = AddQuote(
            names.zip(texts).mapIndexed { idx, (name, text) -> Quote.Line(idx, name, text) }
        )
        quotes.execute(addQuote)
            .map { hxRedirectResponse() }
            .andHandleFailure()
    }

    POST("share/{quoteId}") { request ->
        val quoteId: QuoteId = request.pathVariable("quoteId").let { QuoteId.fromString(it) }
        quotes.execute(ShareQuote(quoteId))
            .map { quoteShare ->
                val quoteShareUrl = request.uriBuilder().replacePath(baseUiUrl).queryParam("share",quoteShare.value).build()

                ServerResponse.status(HttpStatus.OK)
                    .contentType(MediaType.TEXT_HTML)
                    .body(
                        partial {
                            shareQuoteModal(quoteShareUrl)
                        }
                    )
            }.andHandleFailure()
    }
}

private fun Result<Failure, ServerResponse>.andHandleFailure() =
    recover { failure ->
        ServerResponse.status(HttpStatus.OK)
            .body(partial { errorMessage("Oopsie! Something broke!", failure.message) })
    }.get()

private fun hxRedirectResponse() = ServerResponse.status(HttpStatus.OK)
    .header("HX-Redirect", baseUiUrl)
    .build()

fun wrapper(title: String, block: BODY.() -> Unit) =
    StringWriter().appendHTML().html {
        head {
            title { +title }
            meta(charset = "utf-8")
            meta(name = "viewport", content = "width=device-width, initial-scale=1")
            link(
                rel = "stylesheet",
                href = "https://cdn.jsdelivr.net/npm/bulma@1.0.1/css/versions/bulma-no-dark-mode.min.css"
            )
            link(
                rel = "stylesheet",
                href = "https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.6.0/css/all.min.css"
            )
            script(src = "https://unpkg.com/htmx.org@2.0.0") {}
            script(src = "https://unpkg.com/hyperscript.org@0.9.12") {}
        }
        body {
            hero()
            div(classes = "block") { id = "errorMessages" }
            block()
            div { id = "modals-here" }
        }
    }.toString()

fun partial(block: FlowContent.() -> Unit): String {
    val writer = StringWriter()
    val consumer = writer.appendHTML()
    // hacky stuff so we don't have to return a wrapper div
    object : FlowContent {
        override val consumer = consumer
        override val attributes: MutableMap<String, String>
            get() = mutableMapOf()
        override val attributesEntries: Collection<Map.Entry<String, String>>
            get() = emptyList()
        override val emptyTag: Boolean
            get() = true
        override val inlineTag: Boolean
            get() = true
        override val namespace: String?
            get() = null
        override val tagName: String
            get() = ""
    }.block()
    return writer.toString()
}

fun String.path(vararg paths: String): String =
    buildList {
        add(this@path)
        addAll(paths)
    }.joinToString("/")