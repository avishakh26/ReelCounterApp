package com.reelcounter.core.designsystem.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.reelcounter.core.designsystem.R
import com.reelcounter.core.designsystem.icon.RcIcons

/**
 * A full-width outlined text field.
 *
 * @param errorText replaces [supportingText] and puts the field in its error state.
 * @param maxLength shows a "12/160" counter. It doesn't truncate; the caller decides.
 * @param autofill lets password managers and the system autofill the field.
 */
@Composable
fun RcTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    errorText: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLength: Int? = null,
    prefix: String? = null,
    autofill: ContentType? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    textStyle: TextStyle = LocalTextStyle.current,
) {
    val message = errorText ?: supportingText
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .then(if (autofill != null) Modifier.semantics { contentType = autofill } else Modifier),
        enabled = enabled,
        label = { Text(text = label) },
        prefix = if (prefix != null) {
            { Text(text = prefix) }
        } else {
            null
        },
        supportingText = if (message != null || maxLength != null) {
            {
                Row {
                    Text(text = message.orEmpty(), modifier = Modifier.weight(1f))
                    if (maxLength != null) Text(text = "${value.length}/$maxLength")
                }
            }
        } else {
            null
        },
        isError = errorText != null,
        textStyle = textStyle,
        shape = MaterialTheme.shapes.medium,
        colors = rcTextFieldColors(),
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
    )
}

/** Field colors: a card-colored fill, a hairline border at rest and the accent when focused. */
@Composable
fun rcTextFieldColors() = MaterialTheme.colorScheme.let { colors ->
    OutlinedTextFieldDefaults.colors(
        focusedContainerColor = colors.surfaceContainerLowest,
        unfocusedContainerColor = colors.surfaceContainerLowest,
        disabledContainerColor = colors.surfaceContainerLow,
        errorContainerColor = colors.surfaceContainerLowest,
        unfocusedBorderColor = colors.outlineVariant,
        focusedBorderColor = colors.primary,
        disabledBorderColor = colors.outlineVariant.copy(alpha = 0.6f),
        unfocusedLabelColor = colors.onSurfaceVariant,
        unfocusedLeadingIconColor = colors.onSurfaceVariant,
        focusedLeadingIconColor = colors.onSurfaceVariant,
    )
}

/**
 * A password field with a show/hide toggle. Use [isNewPassword] when choosing a password, so
 * password managers offer to generate and save one instead of filling an existing one.
 */
@Composable
fun RcPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    errorText: String? = null,
    enabled: Boolean = true,
    isNewPassword: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    RcTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = modifier,
        supportingText = supportingText,
        errorText = errorText,
        enabled = enabled,
        autofill = if (isNewPassword) ContentType.NewPassword else ContentType.Password,
        keyboardOptions = keyboardOptions.copy(keyboardType = KeyboardType.Password),
        keyboardActions = keyboardActions,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) RcIcons.HidePassword else RcIcons.ShowPassword,
                    contentDescription = stringResource(
                        if (visible) R.string.core_designsystem_hide_password else R.string.core_designsystem_show_password,
                    ),
                )
            }
        },
    )
}
