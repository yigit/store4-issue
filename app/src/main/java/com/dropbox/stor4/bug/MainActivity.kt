package com.dropbox.stor4.bug

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.dropbox.android.external.store4.*
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import one.block.eosiojava.models.rpcProvider.request.GetBlockRequest
import one.block.eosiojava.models.rpcProvider.response.GetBlockResponse
import one.block.eosiojavarpcprovider.implementations.EosioJavaRpcProviderImpl
import java.math.BigInteger
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    companion object {
        private const val totalBlocks = 5L
    }

    private val rpc = EosioJavaRpcProviderImpl("https://eos.greymass.com/")
    private val blockNumberStore = StoreBuilder
        .fromNonFlow<Unit, BigInteger> {
            rpc.info.headBlockNum
        }.build()
    private val blockInfoStore = StoreBuilder
        .fromNonFlow<BigInteger, GetBlockResponse> {
            rpc.getBlock(GetBlockRequest(it.toString()))
        }.cachePolicy(
            MemoryPolicy.builder()
                .setMemorySize(totalBlocks)
                .setExpireAfterAccess(10)
                .setExpireAfterTimeUnit(TimeUnit.MINUTES)
                .build()
        )
        .build()
    private var fetchedBlocks = 0L
    private lateinit var statuses: Array<TextView>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        statuses = arrayOf(status1, status2, status3, status4, status5)
        runStoreStreamTest()
    }

    private fun runStoreStreamTest()  {
        repeat.isEnabled = false
        fetchedBlocks = 0
        blockNumberStore.stream(StoreRequest.fresh(Unit)).execute {
            for (i in 0 until totalBlocks) {
                val block = it.minus(BigInteger.valueOf(i))
                inUI {
                    statuses[i.toInt()].text = "Fetching block #$block"
                }
                blockInfoStore.stream(StoreRequest.cached(block, true)).execute({
                    countBlocks(block)
                }) {
                    inUI {
                        statuses[i.toInt()].text = "Block #${it.blockNum} is fetched"
                    }
                }
                delay(250)
            }
        }
    }

    private suspend fun countBlocks(block: BigInteger) {
        fetchedBlocks++
        Log.d(MainActivity::class.simpleName, "$fetchedBlocks $block")
        if (fetchedBlocks == totalBlocks) {
            inUI {
                Snackbar.make(
                    statuses[4],
                    "Test is complete",
                    Snackbar.LENGTH_LONG
                ).show()
                repeat.isEnabled = true
            }
        }
    }

    private fun <T> Flow<StoreResponse<T>>.execute(onCount: (suspend () -> Unit)? = null,
                                                   run: suspend (T) -> Unit) = inBackground {
        collect {
            val error = it.errorOrNull()
            val result = it.dataOrNull()
            if (result != null)
                run(result)
            else if (error != null)
                inUI {
                    Snackbar.make(statuses[4], "Oops! ${error.message}", Snackbar.LENGTH_LONG)
                        .show()
                }
            if (it.origin == ResponseOrigin.Fetcher && it !is StoreResponse.Loading && onCount != null) {
                Log.d(MainActivity::class.simpleName, "$it")
                onCount()
            }
        }
    }

    private fun inBackground(run: suspend () -> Unit) = lifecycleScope.launch {
        withContext(Dispatchers.IO) {
            run()
        }
    }

    private suspend fun inUI(uiThread: () -> Unit) = withContext(Dispatchers.Main) {
        uiThread()
    }

    fun onRepeatTest(view: View) {
        runStoreStreamTest()
    }
}
