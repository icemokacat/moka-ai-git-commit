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
import com.intellij.openapi.vcs.CommitMessageI
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.ui.CommitMessage
import com.intellij.vcs.commit.AbstractCommitWorkflowHandler
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
        //val changes = ChangeListManager.getInstance(project).defaultChangeList.changes.toList()
        //if (changes.isEmpty()) {
        //    WriteCommandAction.runWriteCommandAction(project) { document.setText("No changes to commit.") }
        //    return
        //}

        val commitWorkflowHandler = e.getData(VcsDataKeys.COMMIT_WORKFLOW_HANDLER) as? AbstractCommitWorkflowHandler<*, *>
        val commitMessage = getCommitMessage(e)

        if(commitWorkflowHandler == null) {
            notify(project, "Commit workflow handler not found. This action must be triggered from the commit dialog.", NotificationType.ERROR)
            return
        }
        if(commitMessage == null) {
            notify(project, "Commit message not found.", NotificationType.ERROR)
        }

        val includedChanges = commitWorkflowHandler.ui.getIncludedChanges()
        val includedUnversionedFiles = commitWorkflowHandler.ui.getIncludedUnversionedFiles()

        if (includedChanges.isEmpty() && includedUnversionedFiles.isEmpty()) {
            //WriteCommandAction.runWriteCommandAction(project) { document.setText("No changes to commit.") }
            commitMessage?.setCommitMessage("No changes to commit.")
            return
        }

        val settings = AppSettings.getInstance()

        if (settings.state.promptTemplate.isBlank()) {
            notify(project, "Prompt template is empty. Go to Settings → Tools → Moka Git AI Commit.", NotificationType.WARNING)
            return
        }

        val promptTemplate = settings.state.promptTemplate.trimEnd()
        val modality = ModalityState.current()

        commitMessage?.setCommitMessage("Generating commit message...")

        object : Task.Backgroundable(project, "Generating commit message...", true) {
            override fun run(indicator: ProgressIndicator) {
                val diff = DiffExtractor.extractFromWorkflow(includedChanges, includedUnversionedFiles, project.basePath)
                val prompt = promptTemplate + "\n\n" + diff
                settings.state.lastPrompt = prompt

                val savedApiUrl = settings.state.openAiBaseUrl;
                if (savedApiUrl.isBlank()) {
                    notify(project, "API URL not set. Go to Settings → Tools → Moka Git AI Commit.", NotificationType.WARNING)
                    return
                }

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

    private fun getCommitMessage(e: AnActionEvent): CommitMessage? {
        return e.getData<CommitMessageI?>(VcsDataKeys.COMMIT_MESSAGE_CONTROL) as CommitMessage?
    }

    private fun notify(project: Project, content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("com.moka.gitcommit")
            .createNotification(content, type)
            .notify(project)
    }
}
