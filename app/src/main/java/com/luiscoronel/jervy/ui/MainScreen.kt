package com.luiscoronel.jervy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.luiscoronel.jervy.R
import com.luiscoronel.jervy.system.ServiceStatus
import com.luiscoronel.jervy.ui.theme.JervyTheme

@Composable
fun MainScreen(
    status: ServiceStatus,
    permissionsMissing: Boolean,
    onActivate: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text =
                stringResource(
                    when (status) {
                        ServiceStatus.STOPPED -> R.string.status_stopped
                        ServiceStatus.ACTIVE -> R.string.status_active
                        ServiceStatus.PAUSED -> R.string.status_paused
                    },
                ),
            style = MaterialTheme.typography.titleLarge,
        )
        when (status) {
            ServiceStatus.STOPPED -> Button(onClick = onActivate) { Text(stringResource(R.string.action_activate)) }
            ServiceStatus.ACTIVE -> Button(onClick = onPause) { Text(stringResource(R.string.action_pause)) }
            ServiceStatus.PAUSED -> Button(onClick = onResume) { Text(stringResource(R.string.action_resume)) }
        }
        if (permissionsMissing) {
            Text(
                text = stringResource(R.string.permissions_missing),
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MainScreenPreview() {
    JervyTheme {
        MainScreen(
            status = ServiceStatus.STOPPED,
            permissionsMissing = true,
            onActivate = {},
            onPause = {},
            onResume = {},
        )
    }
}
