package com.videodownloader.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.videodownloader.databinding.ActivityMainBinding
import com.videodownloader.util.DownloadService
import com.videodownloader.viewmodel.MainViewModel
import com.videodownloader.viewmodel.UiState

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        handleSharedIntent(intent)
        observeViewModel()
        setupButtons()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleSharedIntent(it) }
    }

    // קליטת קישור ששותף מאפליקציה אחרת
    private fun handleSharedIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_SEND &&
            intent.type == "text/plain") {
            val sharedUrl = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedUrl.isNullOrBlank()) {
                binding.etUrl.setText(sharedUrl)
                viewModel.fetchVideo(sharedUrl)
            }
        }
    }

    private fun setupButtons() {
        binding.btnFetch.setOnClickListener {
            val url = binding.etUrl.text.toString().trim()
            viewModel.fetchVideo(url)
        }

        binding.btnDownload.setOnClickListener {
            val state = viewModel.uiState.value
            if (state is UiState.Success) {
                DownloadService.start(
                    context = this,
                    url = state.videoInfo.downloadUrl,
                    title = state.videoInfo.title
                )
                binding.tvStatus.text = "ההורדה התחילה! בדוק את ההתראות"
            }
        }
    }

    private fun observeViewModel() {
        viewModel.uiState.observe(this) { state ->
            when (state) {
                is UiState.Idle -> {
                    binding.progressBar.visibility = View.GONE
                    binding.cardResult.visibility = View.GONE
                    binding.tvStatus.text = ""
                }

                is UiState.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.cardResult.visibility = View.GONE
                    binding.tvStatus.text = "טוען מידע על הסרטון..."
                    binding.btnFetch.isEnabled = false
                }

                is UiState.Success -> {
                    binding.progressBar.visibility = View.GONE
                    binding.cardResult.visibility = View.VISIBLE
                    binding.btnFetch.isEnabled = true
                    binding.tvStatus.text = "✓ נמצא סרטון מ-${state.videoInfo.source}"

                    binding.tvVideoTitle.text = state.videoInfo.title

                    Glide.with(this)
                        .load(state.videoInfo.thumbnailUrl)
                        .centerCrop()
                        .into(binding.ivThumbnail)
                }

                is UiState.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.cardResult.visibility = View.GONE
                    binding.btnFetch.isEnabled = true
                    binding.tvStatus.text = "❌ ${state.message}"
                }
            }
        }
    }
}
