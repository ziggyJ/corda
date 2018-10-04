package net.corda.node.services.config

import java.io.InputStream
import java.io.Reader
import java.nio.file.Path
import java.util.*

internal open class Konfiguration : Configuration {

    override fun <EXPECTED_VALUE> get(key: String): EXPECTED_VALUE {
        TODO("sollecitom not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun mutable() = Konfiguration.Mutable()

    class Mutable : Konfiguration(), Configuration.Mutable {

        override fun set(key: String, value: Any?) {
            TODO("sollecitom not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun mutable() = this
    }

    class Builder : Configuration.Builder {

        override val from: Configuration.Builder.SourceSelector
            get() = TODO("sollecitom not implemented") //To change initializer of created properties use File | Settings | File Templates.

        override fun build(): Configuration {
            TODO("sollecitom not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        class SourceSelector : Configuration.Builder.SourceSelector {
            override fun systemProperties(prefixFilter: String?): Configuration.Builder {
                TODO("sollecitom not implemented") //To change body of created functions use File | Settings | File Templates.
            }

            override fun environment(): Configuration.Builder {
                TODO("sollecitom not implemented") //To change body of created functions use File | Settings | File Templates.
            }

            override fun properties(properties: Properties): Configuration.Builder {
                TODO("sollecitom not implemented") //To change body of created functions use File | Settings | File Templates.
            }

            override fun map(map: Map<String, Any?>): Configuration.Builder {
                TODO("sollecitom not implemented") //To change body of created functions use File | Settings | File Templates.
            }

            override fun hierarchicalMap(map: Map<String, Any?>): Configuration.Builder {
                TODO("sollecitom not implemented") //To change body of created functions use File | Settings | File Templates.
            }

            override val hocon: Configuration.Builder.SourceSelector.FormatAware
                get() = TODO("sollecitom not implemented") //To change initializer of created properties use File | Settings | File Templates.
            override val yaml: Configuration.Builder.SourceSelector.FormatAware
                get() = TODO("sollecitom not implemented") //To change initializer of created properties use File | Settings | File Templates.
            override val xml: Configuration.Builder.SourceSelector.FormatAware
                get() = TODO("sollecitom not implemented") //To change initializer of created properties use File | Settings | File Templates.
            override val json: Configuration.Builder.SourceSelector.FormatAware
                get() = TODO("sollecitom not implemented") //To change initializer of created properties use File | Settings | File Templates.

            class FormatAware : Configuration.Builder.SourceSelector.FormatAware {

                override fun file(path: Path): Configuration.Builder {
                    TODO("sollecitom not implemented") //To change body of created functions use File | Settings | File Templates.
                }

                override fun resource(resourceName: String): Configuration.Builder {
                    TODO("sollecitom not implemented") //To change body of created functions use File | Settings | File Templates.
                }

                override fun reader(reader: Reader): Configuration.Builder {
                    TODO("sollecitom not implemented") //To change body of created functions use File | Settings | File Templates.
                }

                override fun inputStream(stream: InputStream): Configuration.Builder {
                    TODO("sollecitom not implemented") //To change body of created functions use File | Settings | File Templates.
                }

                override fun string(rawFormat: String): Configuration.Builder {
                    TODO("sollecitom not implemented") //To change body of created functions use File | Settings | File Templates.
                }

                override fun bytes(bytes: ByteArray): Configuration.Builder {
                    TODO("sollecitom not implemented") //To change body of created functions use File | Settings | File Templates.
                }
            }
        }
    }
}