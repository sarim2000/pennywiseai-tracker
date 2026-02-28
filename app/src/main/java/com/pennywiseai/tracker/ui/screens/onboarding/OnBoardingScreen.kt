package com.pennywiseai.tracker.ui.screens.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import com.pennywiseai.tracker.ui.effects.overScrollVertical
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.ui.theme.Spacing

@Composable
fun OnBoardingScreen(
    onOnboardingComplete: () -> Unit,
    viewModel: OnBoardingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val stepOrder = remember {
        OnBoardingStep.entries.toList()
    }
    Scaffold(
        bottomBar = {
            OnBoardingBottomBar(
                uiState = uiState,
                onBack = { viewModel.goToPreviousStep() },
                onNext = {
                    when (uiState.currentStep) {
                        OnBoardingStep.WELCOME -> viewModel.goToNextStep()
                        OnBoardingStep.PROFILE -> viewModel.goToNextStep()
                        OnBoardingStep.PERMISSIONS -> viewModel.goToNextStep()
                        OnBoardingStep.SMS_SCAN -> viewModel.goToNextStep()
                        OnBoardingStep.ACCOUNT_SETUP -> {
                            viewModel.completeOnboarding(onOnboardingComplete)
                        }
                    }
                },
                onSkip = {
                    when (uiState.currentStep) {
                        OnBoardingStep.PERMISSIONS -> {
                            viewModel.skipSmsPermission()
                            viewModel.goToNextStep()
                        }
                        OnBoardingStep.SMS_SCAN -> viewModel.goToNextStep()
                        OnBoardingStep.ACCOUNT_SETUP -> {
                            viewModel.completeOnboarding(onOnboardingComplete)
                        }
                        else -> viewModel.goToNextStep()
                    }
                },
                onStartScan = { viewModel.startSmsScan() }
            )
        }
    ) { innerPadding ->
        AnimatedContent(
            targetState = uiState.currentStep,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            transitionSpec = {
                val targetIndex = stepOrder.indexOf(targetState)
                val initialIndex = stepOrder.indexOf(initialState)
                if (targetIndex > initialIndex) {
                    slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
                } else {
                    slideInHorizontally { -it } togetherWith slideOutHorizontally { it }
                }
            },
            label = "onboarding_step"
        ) { step ->
            when (step) {
                OnBoardingStep.WELCOME -> WelcomeStep()
                OnBoardingStep.PROFILE -> ProfileStep(
                    uiState = uiState,
                    viewModel = viewModel
                )
                OnBoardingStep.PERMISSIONS -> PermissionsStep(
                    uiState = uiState,
                    onPermissionResult = { viewModel.onSmsPermissionResult(it) }
                )
                OnBoardingStep.SMS_SCAN -> SmsScanStep(uiState = uiState)
                OnBoardingStep.ACCOUNT_SETUP -> AccountSetupStep(
                    uiState = uiState,
                    onSelectAccount = { viewModel.selectAccount(it) }
                )
            }
        }
    }
}

@Composable
private fun WelcomeStep() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.mipmap.ic_launcher_foreground),
            contentDescription = "PennyWise",
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.height(Spacing.xl))

        Text(
            text = "Welcome to PennyWise",
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(Spacing.md))

        Text(
            text = "Your AI-powered expense tracker that automatically detects transactions from SMS messages.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(Spacing.lg))

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(Spacing.md)) {
                Text(
                    text = "What you'll set up:",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
                Text(
                    text = "1. Your profile\n2. SMS permissions for auto-detection\n3. Initial transaction scan\n4. Your main bank account",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileStep(
    uiState: OnBoardingUiState,
    viewModel: OnBoardingViewModel
) {
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.selectProfileImage(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .overScrollVertical()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(Spacing.xl))

        Text(
            text = "What should we call you?",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(Spacing.lg))

        OutlinedTextField(
            value = uiState.userName,
            onValueChange = { viewModel.updateUserName(it) },
            label = { Text("Your name") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(Spacing.xl))

        Text(
            text = "Choose an avatar",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(Spacing.sm))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(viewModel.avatarDrawables) { index, drawableRes ->
                val isSelected = uiState.profileImageUri == null && uiState.selectedAvatarIndex == index
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .then(
                            if (isSelected) Modifier.border(
                                3.dp,
                                MaterialTheme.colorScheme.primary,
                                CircleShape
                            ) else Modifier
                        )
                        .clickable { viewModel.selectAvatar(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = drawableRes),
                        contentDescription = "Avatar ${index + 1}",
                        modifier = Modifier.size(40.dp),
                        colorFilter = ColorFilter.tint(
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }

            item {
                // Gallery picker option
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(
                            if (uiState.profileImageUri != null) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .then(
                            if (uiState.profileImageUri != null) Modifier.border(
                                3.dp,
                                MaterialTheme.colorScheme.primary,
                                CircleShape
                            ) else Modifier
                        )
                        .clickable { imagePicker.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    if (uiState.profileImageUri != null) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Photo selected",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    } else {
                        Text(
                            text = "+",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(Spacing.xl))

        Text(
            text = "Pick a background color",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(Spacing.sm))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier.fillMaxWidth()
        ) {
            viewModel.backgroundColors.forEachIndexed { index, colorInt ->
                val isSelected = uiState.selectedBackgroundColor == index
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(colorInt))
                        .then(
                            if (isSelected) Modifier.border(
                                3.dp,
                                MaterialTheme.colorScheme.onSurface,
                                CircleShape
                            ) else Modifier
                        )
                        .clickable { viewModel.selectBackgroundColor(index) },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionsStep(
    uiState: OnBoardingUiState,
    onPermissionResult: (Boolean) -> Unit
) {
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val readSmsGranted = permissions[Manifest.permission.READ_SMS] == true
        onPermissionResult(readSmsGranted)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .overScrollVertical()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.MailOutline,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(Spacing.lg))

        Text(
            text = "Enable Automatic Detection",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(Spacing.md))

        Text(
            text = "PennyWise can automatically detect and categorize your bank transactions from SMS messages.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(Spacing.lg))

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(Spacing.md)) {
                Text(
                    text = "Your Privacy Matters",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
                Text(
                    text = "\u2022 Only transaction messages are processed\n" +
                            "\u2022 All data stays on your device\n" +
                            "\u2022 No personal messages are read\n" +
                            "\u2022 You can revoke access anytime in Settings",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.xl))

        if (uiState.smsPermissionGranted) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Text(
                        text = "Permissions granted! Tap Continue to proceed.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        } else {
            Button(
                onClick = {
                    val permissions = mutableListOf(
                        Manifest.permission.READ_SMS,
                        Manifest.permission.RECEIVE_SMS
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    permissionLauncher.launch(permissions.toTypedArray())
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Enable Permissions")
            }
        }
    }
}

@Composable
private fun SmsScanStep(uiState: OnBoardingUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (uiState.isScanning) {
            CircularProgressIndicator(
                modifier = Modifier.size(64.dp)
            )

            Spacer(modifier = Modifier.height(Spacing.lg))

            Text(
                text = "Scanning your messages...",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(Spacing.md))

            if (uiState.scanTotal > 0) {
                val progress = uiState.scanProcessed.toFloat() / uiState.scanTotal.toFloat()
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
                Text(
                    text = "${uiState.scanProcessed} / ${uiState.scanTotal} messages processed",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (uiState.scanParsed > 0) {
                    Text(
                        text = "${uiState.scanParsed} transactions found",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (uiState.scanEstimatedRemaining > 0) {
                    val seconds = uiState.scanEstimatedRemaining / 1000
                    Text(
                        text = "~${seconds}s remaining",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(Spacing.sm))
                Text(
                    text = "Preparing scan...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (uiState.scanCompleted) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(Spacing.lg))

            Text(
                text = "Scan Complete!",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(Spacing.md))

            if (uiState.scanSaved > 0) {
                Text(
                    text = "${uiState.scanSaved} transactions saved from ${uiState.scanTotal} messages",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "No transactions found. You can add them manually later.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            // Not started yet
            Icon(
                imageVector = Icons.Filled.MailOutline,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(Spacing.lg))

            Text(
                text = "Scan Your Messages",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(Spacing.md))

            Text(
                text = "We'll scan your SMS messages to find bank transactions and set up your accounts automatically.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AccountSetupStep(
    uiState: OnBoardingUiState,
    onSelectAccount: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .overScrollVertical()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(Spacing.xl))

        if (uiState.accounts.isEmpty()) {
            Icon(
                imageVector = Icons.Filled.AccountBalance,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(Spacing.lg))

            Text(
                text = "You're all set!",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(Spacing.md))

            Text(
                text = "No accounts were detected yet. You can set up your main account later in Settings.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                text = "Select Your Main Account",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(Spacing.md))

            Text(
                text = "Choose the account you use most often. This will be shown on your home screen.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(Spacing.lg))

            uiState.accounts.forEach { account ->
                val accountKey = "${account.bankName}_${account.accountLast4}"
                val isSelected = uiState.selectedAccountKey == accountKey

                Card(
                    onClick = { onSelectAccount(accountKey) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    border = if (isSelected) BorderStroke(
                        2.dp,
                        MaterialTheme.colorScheme.primary
                    ) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Spacing.xs)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = account.bankName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "****${account.accountLast4}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${account.currency} ${account.balance}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (isSelected) {
                            Spacer(modifier = Modifier.width(Spacing.sm))
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepIndicator(
    currentStep: OnBoardingStep,
    modifier: Modifier = Modifier
) {
    val steps = OnBoardingStep.entries
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEachIndexed { index, step ->
            val isActive = step == currentStep
            val isPast = index < steps.indexOf(currentStep)
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(if (isActive) 8.dp else 6.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isActive -> MaterialTheme.colorScheme.primary
                            isPast -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                        }
                    )
            )
        }
    }
}

@Composable
private fun OnBoardingBottomBar(
    uiState: OnBoardingUiState,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onStartScan: () -> Unit
) {
    val isFirstStep = uiState.currentStep == OnBoardingStep.WELCOME
    val canGoBack = !isFirstStep && !uiState.isScanning

    Column {
        StepIndicator(
            currentStep = uiState.currentStep,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.sm)
        )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Back button
        if (canGoBack) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
        } else {
            Spacer(modifier = Modifier.width(48.dp))
        }

        // Skip / CTA button area
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (uiState.currentStep) {
                OnBoardingStep.WELCOME -> {
                    Button(onClick = onNext) {
                        Text("Get Started")
                    }
                }

                OnBoardingStep.PROFILE -> {
                    Button(
                        onClick = onNext,
                        enabled = uiState.userName.isNotBlank()
                    ) {
                        Text("Save & Continue")
                    }
                }

                OnBoardingStep.PERMISSIONS -> {
                    if (!uiState.smsPermissionGranted) {
                        TextButton(onClick = onSkip) {
                            Text("Skip")
                        }
                    }
                    if (uiState.smsPermissionGranted) {
                        Button(onClick = onNext) {
                            Text("Continue")
                        }
                    }
                }

                OnBoardingStep.SMS_SCAN -> {
                    if (!uiState.isScanning && !uiState.scanCompleted) {
                        TextButton(onClick = onSkip) {
                            Text("Skip")
                        }
                        Button(onClick = onStartScan) {
                            Text("Start Scanning")
                        }
                    } else if (uiState.isScanning) {
                        TextButton(onClick = onSkip) {
                            Text("Skip")
                        }
                    } else if (uiState.scanCompleted) {
                        Button(onClick = onNext) {
                            Text("Continue")
                        }
                    }
                }

                OnBoardingStep.ACCOUNT_SETUP -> {
                    if (uiState.accounts.isEmpty()) {
                        Button(onClick = onNext) {
                            Text("Finish")
                        }
                    } else {
                        if (uiState.selectedAccountKey == null) {
                            TextButton(onClick = onSkip) {
                                Text("Skip")
                            }
                        }
                        Button(
                            onClick = onNext,
                            enabled = uiState.selectedAccountKey != null || uiState.accounts.isEmpty()
                        ) {
                            if (uiState.isCompleting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Finish")
                            }
                        }
                    }
                }
            }
        }
    }
    }
}
