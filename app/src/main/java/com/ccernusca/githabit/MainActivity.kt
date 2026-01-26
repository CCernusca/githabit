package com.ccernusca.githabit

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalUriHandler
import coil.compose.AsyncImage
import com.ccernusca.githabit.ui.theme.GitHabitTheme
import kotlinx.coroutines.delay
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class GitHubUser(
    val login: String,
    @SerialName("avatar_url") val avatarUrl: String,
    val bio: String?,
    @SerialName("public_repos") val publicRepos: Int
)

@Serializable
data class GitHubRepo(
    val id: Long,
    val name: String,
    @SerialName("full_name") val fullName: String,
    val description: String?,
    @SerialName("html_url") val htmlUrl: String,

    // Repository Stats
    @SerialName("stargazers_count") val stars: Int,
    @SerialName("forks_count") val forks: Int,
    @SerialName("watchers_count") val watchers: Int,

    // Language Info
    val language: String?,

    // State flags
    val private: Boolean,
    @SerialName("updated_at") val updatedAt: String
)

object GitHubService {
    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true // Critical: GitHub returns many fields we don't need
                coerceInputValues = true
            })
        }
    }

    suspend fun getUserData(username: String, token: String? = null): GitHubUser {
        return client.get("https://api.github.com/users/$username") {
            headers {
                append("Accept", "application/vnd.github.v3+json")
                if (token != null) {
                    append("Authorization", "Bearer $token")
                }
            }
        }.body()
    }

    suspend fun getReposData(username: String): List<GitHubRepo> {
        return client.get("https://api.github.com/users/$username/repos") {
            parameter("sort", "updated") // Get most recently active repos first
            parameter("per_page", 100)   // Maximize results per page
        }.body()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GitHabitTheme {
                App()
            }
        }
    }
}

@SuppressLint("CoroutineCreationDuringComposition")
@Composable
fun App() {
    // Reference to the keyboard, so it can be hidden
    val keyboardController = LocalSoftwareKeyboardController.current

    // Variable for holding the retrieved GitHub data
    var userData: GitHubUser? by remember { mutableStateOf(null) }

    // Variable for storing whether the entered handle is valid
    var invalidData by remember { mutableStateOf(false) }

    // Variable for storing potential data retrieval errors
    var retrievalError: Exception? by remember { mutableStateOf(null) }

    // Variable for storing retrieved repo data
    var reposData: List<GitHubRepo>? by remember { mutableStateOf(null) }

    // Scope for running suspend functions for getting GitHub data
    val scope = rememberCoroutineScope()

    // Grab the context automatically from the environment
    val context = LocalContext.current

    // Manager for persistent data (GitHub handle)
    val dataManager = remember { DataManager(context) }

    // Collect the data from storage as a state
    val savedHandle by dataManager.getHandle.collectAsState(initial = "")
    // Local state for immediate UI updates
    var typedText by remember { mutableStateOf("") }

    // URI handler
    val uriHandler = LocalUriHandler.current

    // Function for retrieving data from GitHub
    fun retrieveData() {
        scope.launch {
            // Get user
            try {
                userData = GitHubService.getUserData(savedHandle)
                invalidData = false
                retrievalError = null
                Log.i("UserRetrieval", "User Data Retrieved: $userData")
            } catch (e: io.ktor.serialization.JsonConvertException) {
                userData = null
                invalidData = true
                retrievalError = null
                Log.w("UserRetrieval", "Wrong data returned, no valid data found, Error: $e")
            } catch (e: Exception) {
                userData = null
                invalidData = false
                retrievalError = e
                Log.e("UserRetrieval", "GitHub User Data Retrieval Error: $e")
            }
            // Get repos
            try {
                reposData = GitHubService.getReposData(savedHandle)
                Log.i("ReposRetrieval", "Repos Data Retrieved: $reposData")
            } catch (e: io.ktor.serialization.JsonConvertException) {
                reposData = null
                Log.w("ReposRetrieval", "Wrong data returned, no valid data found, Error: $e")
            } catch (e: Exception) {
                reposData = null
                Log.e("ReposRetrieval", "GitHub ReposData Retrieval Error: $e")
            }
        }
    }

    // Sync local state when the savedHandle is first loaded
    LaunchedEffect(savedHandle) {
        if (typedText.isEmpty()) typedText = savedHandle
        // Get data on start if handle already entered
        if (!savedHandle.isEmpty()) retrieveData()
    }

    // Debounce Logic - Handle is only saved once user stops typing
    LaunchedEffect(typedText) {
        // If the text matches what's already saved, don't do anything
        if (typedText == savedHandle) return@LaunchedEffect

        // Wait 500ms. If typedText changes again during this time, this block cancels and starts over.
        delay(500L)

        dataManager.saveHandle(typedText)
    }

    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        // Title
        Text(
            text = "GitHabit",
            fontSize = 64.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 0.dp)
        )

        // Reminder to enter handle if empty
        if (savedHandle == "") {
            Text(
                text = "Please enter your GitHub handle!",
                color = Color.Red,
                textAlign = TextAlign.Justify,
                modifier = Modifier.padding(0.dp)
            )
        }

        // Handle input field
        TextField(
            value = typedText,
            onValueChange = {
                // Filter out newlines manually just in case of a paste
                typedText = it.replace("\n", "")
            }, // UI updates instantly
            singleLine = true, // Forces the text into one horizontal line
            maxLines = 1,      // Prevents the box from growing vertically
            label = { Text("GitHub handle") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 0.dp),
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Done // Changes 'Enter' to a 'Checkmark/Done' icon
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    // Hide keyboard when Done is pressed
                    keyboardController?.hide()

                    // Get GitHub data
                    retrieveData()
                }
            )
        )

        // Display message if handle is invalid
        if (invalidData) {
            Text(
                text = "Invalid Github handle or Rate limit exceeded",
                color = Color.Red
            )
        }

        // Display potential errors
        retrievalError?.let {
            Text(
                text = "$retrievalError"
            )
        }

        // Last commit
        reposData?.let { repos ->
            if (!repos.isEmpty()) {
                Text(
                    text = "Last Commit: ${repos[0].updatedAt.split("T")[0]}",
                    color = Color.Blue,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(24.dp),
                    lineHeight = 64.sp
                )
            } else {
                Text(
                    text = "Last Commit: NEVER",
                    color = Color.Blue,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(24.dp),
                    lineHeight = 64.sp
                )
            }
        }

        // Display GitHub Profile
        Text(
            text = "GitHub Profile",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold
        )
        // Display information if valid
        userData?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // User avatar
                AsyncImage(
                    model = userData?.avatarUrl,
                    contentDescription = "Profile Picture",
                    modifier = Modifier
                        .size(64.dp)            // Set the size of the image
                        .clip(CircleShape)      // Makes the image round
                        .clickable { uriHandler.openUri("https://github.com/${userData?.login}") },
                    contentScale = ContentScale.Crop // Scales the image to fill the circle
                )

                Spacer(modifier = Modifier.width(16.dp))

                // User information
                if ((userData!!.bio) != null) {
                    Text(
                        text = "Handle: ${userData!!.login}\nPublic Repos: ${userData!!.publicRepos}\nBio: ${userData!!.bio}"
                    )
                } else {
                    Text(
                        text = "Handle: ${userData!!.login}\nPublic Repos: ${userData!!.publicRepos}"
                    )
                }
            }
        } ?: run {
            Text(
                text = "Data could not be loaded"
            )
        }

        // Display GitHub Repos
        Text(
            text = "GitHub Repositories",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold
        )
        // Display information if valid
        reposData?.let { repos ->
            LazyColumn(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                items(repos.size) { repoIndex ->
                    Text(
                        text = repos[repoIndex].name,
                        color = Color(0xFF0366d6), // GitHub's specific blue color
                        modifier = Modifier
                            .padding(2.dp)
                            .clickable {
                                // Construct the repo URL
                                val url = "https://github.com/${userData?.login}/${repos[repoIndex].name}"
                                uriHandler.openUri(url)
                            }
                    )
                }
            }
            if (repos.isEmpty()) {
                Text(
                    text = "No public repositories",
                    modifier = Modifier.padding(2.dp)
                )
            }
        } ?: run {
            Text(text = "Data could not be loaded")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AppPreview() {
    GitHabitTheme {
        App()
    }
}