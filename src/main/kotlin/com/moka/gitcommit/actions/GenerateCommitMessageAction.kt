package com.moka.gitcommit.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.moka.gitcommit.services.OpenAiClient
import com.moka.gitcommit.services.OpenAiConfig
import com.moka.gitcommit.settings.AppSettings
import com.moka.gitcommit.utils.DiffExtractor

class GenerateCommitMessageAction : AnAction() {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val document = e.getData(VcsDataKeys.COMMIT_MESSAGE_DOCUMENT) ?: return
        val changes = ChangeListManager.getInstance(project).defaultChangeList.changes.toList()
        if (changes.isEmpty()) {
            WriteCommandAction.runWriteCommandAction(project) { document.setText("No changes to commit.") }
            return
        }

        val settings = AppSettings.getInstance()

        if (settings.state.promptTemplate.isBlank()) {
            notify(project, "Prompt template is empty. Go to Settings → Tools → Moka Git AI Commit.", NotificationType.WARNING)
            return
        }

        val promptTemplate = settings.state.promptTemplate.trimEnd()
        val modality = ModalityState.current()

        object : Task.Backgroundable(project, "Generating commit message...", true) {
            override fun run(indicator: ProgressIndicator) {
                val diff = DiffExtractor.extract(changes)
                val prompt = promptTemplate + "\n\n" + diff

                val apiKey = settings.apiKey
                if (apiKey.isBlank()) {
                    notify(project, "API key not set. Go to Settings → Tools → Moka Git AI Commit.", NotificationType.WARNING)
                    return
                }
                val config = OpenAiConfig(
                    apiKey = apiKey,
                    apiUrl = settings.state.openAiBaseUrl,
                    model = settings.state.openAiModel
                )
                runCatching { OpenAiClient.generate(config, prompt) }
                    .onSuccess { message ->
                        ApplicationManager.getApplication().invokeLater({
                            WriteCommandAction.runWriteCommandAction(project) { document.setText(message) }
                        }, modality)
                    }
                    .onFailure { err ->
                        notify(project, err.message ?: "Unknown error.", NotificationType.ERROR)
                    }
            }
        }.queue()
    }

    private fun notify(project: Project, content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("com.moka.gitcommit")
            .createNotification(content, type)
            .notify(project)
    }
}
