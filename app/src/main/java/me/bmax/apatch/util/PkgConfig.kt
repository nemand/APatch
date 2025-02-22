package me.bmax.apatch.util

import android.os.Parcelable
import android.util.Log
import androidx.annotation.Keep
import androidx.compose.runtime.Immutable
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.bmax.apatch.APApplication
import me.bmax.apatch.Natives
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import kotlin.concurrent.thread

object PkgConfig {
    private val mutex = Mutex()
    private val TAG = "PkgConfig"

    private val CSV_HEADER = "pkg,exclude,allow,uid,to_uid,sctx"

    @Immutable
    @Parcelize
    @Keep
    data class Config(var pkg: String = "",
                      var exclude: Int = 1,
                      var allow: Int = 0,
                      var profile: Natives.Profile
    ): Parcelable {
        companion object {
            fun fromLine(line: String): Config {
                val sp = line.split(",")
                val profile = Natives.Profile(sp[3].toInt(), sp[4].toInt(), sp[5])
                val config = Config(sp[0], sp[1].toInt(), sp[2].toInt(), profile)
                return config
            }
        }
        fun isDefault(): Boolean {
            return allow == 0 && exclude != 0
        }
        fun toLine(): String {
            return "${pkg},${exclude},${allow},${profile.uid},${profile.toUid},${profile.scontext}"
        }
    }

    public fun readConfigs(): HashMap<String,Config> {
        val configs = HashMap<String,Config>()
        val file = File(APApplication.PACKAGE_CONFIG_FILE)
        if(file.exists()) {
            file.readLines().drop(1).filter { !it.isEmpty() }.forEach {
                Log.d(TAG, it)
                val p = Config.fromLine(it)
                if(! p.isDefault()) {
                    configs[p.pkg] = p
                }
            }
        }
        return configs
    }

    private fun writeConfigs(configs: HashMap<String,Config> ) {
        val file = File(APApplication.PACKAGE_CONFIG_FILE)
        if(!file.parentFile.exists()) file.parentFile.mkdirs()
        val writer = FileWriter(file, false)
        writer.write(CSV_HEADER + '\n')
        configs.values.forEach {
            if(!it.isDefault()) {
                writer.write(it.toLine() + '\n')
            }
        }
        writer.flush()
        writer.close()
    }

    suspend fun changeConfig(config: Config) {
        mutex.withLock {
            thread {
                Natives.su()
                val configs = readConfigs()
                Log.d(TAG, "change config: " + config)
                configs[config.pkg] = config
                writeConfigs(configs)
            }.join()
        }
    }
}