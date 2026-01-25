package com.ccernusca.githabit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ccernusca.githabit.ui.theme.GitHabitTheme
import kotlinx.coroutines.delay

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

@Composable
fun App() {
    // Reference to the keyboard, so it can be hidden
    val keyboardController = LocalSoftwareKeyboardController.current

    // Grab the context automatically from the environment
    val context = LocalContext.current

    // Manager for persistent data (GitHub handle)
    val dataManager = remember { DataManager(context) }

    // Collect the data from storage as a state
    val savedHandle by dataManager.getHandle.collectAsState(initial = "")
    // Local state for immediate UI updates
    var typedText by remember { mutableStateOf("") }

    // Sync local state when the savedHandle is first loaded
    LaunchedEffect(savedHandle) {
        if (typedText.isEmpty()) typedText = savedHandle
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
            textAlign = TextAlign.Justify,
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
            modifier = Modifier.fillMaxWidth().padding(top = 0.dp),
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Done // Changes 'Enter' to a 'Checkmark/Done' icon
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    // Hide keyboard when Done is pressed
                    keyboardController?.hide()
                }
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
fun AppPreview() {
    GitHabitTheme {
        App()
    }
}