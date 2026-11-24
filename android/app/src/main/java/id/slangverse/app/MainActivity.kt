package id.slangverse.app

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider

internal class ParticipantModel(application: Application) : AndroidViewModel(application) {
    val participant = StagingAccountConfig.create(
        BuildConfig.STAGING_SUPABASE_URL, BuildConfig.STAGING_SUPABASE_PUBLISHABLE_KEY
    )?.let { config ->
        val project = config.url.substringAfter("https://").substringBefore('.')
        ResearchParticipant(application, StagingAccountClient(config), project)
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val participant = ViewModelProvider(this)[ParticipantModel::class.java].participant
        setContent { SlangverseApp(participant) }
    }
}
