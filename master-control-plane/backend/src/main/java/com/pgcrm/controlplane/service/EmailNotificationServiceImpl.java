package com.pgcrm.controlplane.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailNotificationServiceImpl implements EmailNotificationService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:noreply@pgcrm.com}")
    private String fromEmail;

    @Async
    @Override
    public void sendWorkspaceLiveEmail(String toEmail, String pgName, String customDomain) {
        log.info("Sending welcome workspace activation email asynchronously to: {}", toEmail);
        
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("Your PG CRM Workspace is Live! 🚀");

            String workspaceUrl = "https://" + customDomain + ".pgcrm.com";
            
            // Build modern premium styled HTML template
            String htmlContent = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="utf-8">
                    <style>
                        body {
                            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
                            background-color: #fafaf9;
                            color: #1c1917;
                            margin: 0;
                            padding: 0;
                            -webkit-font-smoothing: antialiased;
                        }
                        .container {
                            max-width: 600px;
                            margin: 40px auto;
                            background-color: #ffffff;
                            border: 1px solid #e7e5e4;
                            border-radius: 16px;
                            overflow: hidden;
                            box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05), 0 2px 4px -1px rgba(0, 0, 0, 0.03);
                        }
                        .header {
                            background-color: #0c0a09;
                            padding: 40px 32px;
                            text-align: center;
                            border-bottom: 4px solid #7c3aed;
                        }
                        .header h1 {
                            color: #ffffff;
                            margin: 0;
                            font-size: 26px;
                            font-weight: 800;
                            letter-spacing: -0.03em;
                            text-transform: uppercase;
                        }
                        .content {
                            padding: 40px 32px;
                        }
                        .welcome {
                            font-size: 18px;
                            font-weight: 700;
                            margin-top: 0;
                            color: #7c3aed;
                        }
                        .paragraph {
                            font-size: 15px;
                            line-height: 1.6;
                            color: #44403c;
                        }
                        .details {
                            background-color: #f5f5f4;
                            border: 1px solid #e7e5e4;
                            border-radius: 12px;
                            padding: 20px;
                            margin: 28px 0;
                        }
                        .details-table {
                            width: 100%;
                            border-collapse: collapse;
                        }
                        .details-table td {
                            padding: 6px 0;
                            font-size: 14px;
                        }
                        .details-table td.label {
                            font-weight: 700;
                            color: #78716c;
                            width: 130px;
                        }
                        .details-table td.value {
                            color: #1c1917;
                            font-weight: 600;
                        }
                        .btn-container {
                            text-align: center;
                            margin: 36px 0 20px 0;
                        }
                        .btn {
                            display: inline-block;
                            background-color: #7c3aed;
                            color: #ffffff !important;
                            text-decoration: none;
                            padding: 14px 32px;
                            font-weight: 700;
                            font-size: 13px;
                            border-radius: 8px;
                            text-transform: uppercase;
                            letter-spacing: 0.05em;
                            box-shadow: 0 4px 12px rgba(124, 58, 237, 0.25);
                        }
                        .footer {
                            background-color: #f5f5f4;
                            padding: 28px 32px;
                            text-align: center;
                            font-size: 12px;
                            color: #78716c;
                            border-top: 1px solid #e7e5e4;
                        }
                        .footer p {
                            margin: 4px 0;
                        }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <h1>PG CRM Control Plane</h1>
                        </div>
                        <div class="content">
                            <h2 class="welcome">Congratulations! 🎉</h2>
                            <p class="paragraph">
                                We are thrilled to inform you that your dedicated property management portal is fully provisioned and ready for operations.
                            </p>
                            
                            <div class="details">
                                <table class="details-table">
                                    <tr>
                                        <td class="label">PG Name:</td>
                                        <td class="value">__PG_NAME__</td>
                                    </tr>
                                    <tr>
                                        <td class="label">Workspace URL:</td>
                                        <td class="value"><a href="__WORKSPACE_URL__" style="color: #7c3aed; text-decoration: none; font-weight: 700;">__WORKSPACE_HOST__</a></td>
                                    </tr>
                                    <tr>
                                        <td class="label">Access Level:</td>
                                        <td class="value">Owner Administrator</td>
                                    </tr>
                                </table>
                            </div>
                            
                            <p class="paragraph">
                                You can sign in immediately using the credentials you defined during registration. Feel free to start inviting team managers and configuring tenant ledgers.
                            </p>
                            
                            <div class="btn-container">
                                <a href="__WORKSPACE_URL__" class="btn" target="_blank">Enter Workspace</a>
                            </div>
                        </div>
                        <div class="footer">
                            <p><strong>PG CRM SaaS Solutions Inc.</strong></p>
                            <p>This is an automated activation confirmation. Please do not reply directly to this address.</p>
                        </div>
                    </div>
                </body>
                </html>
                """
                .replace("__PG_NAME__", pgName)
                .replace("__WORKSPACE_URL__", workspaceUrl)
                .replace("__WORKSPACE_HOST__", customDomain + ".pgcrm.com");

            helper.setText(htmlContent, true);
            mailSender.send(message);
            
            log.info("Workspace activation email successfully sent to: {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send workspace live activation email to: " + toEmail, e);
        }
    }
}
