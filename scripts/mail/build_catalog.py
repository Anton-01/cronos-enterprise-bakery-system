#!/usr/bin/env python3
"""Generates src/main/resources/mail/catalog.json (the email template catalog of §7.1).

Edit the TEMPLATES list below and re-run: `python3 scripts/mail/build_catalog.py`.
The UI keeps a copy at src/assets/email-templates/catalog.json; keep both in sync.
"""
import json
import pathlib

def v(name, description, sample, kind="text"):
    return {"name": name, "description": description, "sample": sample, "kind": kind}

def heading(t): return {"type": "heading", "text": t}
def p(t): return {"type": "paragraph", "text": t}
def button(label, url): return {"type": "button", "label": label, "url": url}
def code(t): return {"type": "code", "text": t}
def details(*rows): return {"type": "details", "rows": [{"label": l, "value": val} for l, val in rows]}
def callout(tone, t): return {"type": "callout", "tone": tone, "text": t}
def lst(*items): return {"type": "list", "items": list(items)}
def divider(): return {"type": "divider"}
def sig(t): return {"type": "signature", "text": t}

ES_SIG = sig("El equipo de {{ brandName }}")
EN_SIG = sig("The {{ brandName }} team")
ES_IGNORE = p("Si no fuiste tú, ignora este mensaje o escríbenos a {{ supportEmail }}.")
EN_IGNORE = p("If this wasn't you, ignore this message or write to {{ supportEmail }}.")

def fallback_es(var): return p("Si el botón no funciona, copia este enlace en tu navegador: {{ " + var + " }}")
def fallback_en(var): return p("If the button does not work, paste this link into your browser: {{ " + var + " }}")

def tpl(key, audience, transactional, trigger, description, variables, es, en):
    return {"key": key, "audience": audience, "transactional": transactional, "trigger": trigger,
            "description": description, "variables": variables,
            "locales": {"es-MX": es, "en": en}}

def content(subject, preheader, blocks):
    return {"subject": subject, "preheader": preheader, "blocks": blocks}

NAME = v("displayName", "Recipient's display name", "Ana López")

TEMPLATES = [
    tpl("auth.registration-received", "USER", True, "RegistrationSubmittedEvent",
        "Confirms a self-registration and explains that an administrator reviews it.",
        [NAME, v("reference", "Registration reference", "REG-2026-000042")],
        content("Recibimos tu solicitud de registro", "Un administrador la revisará pronto.", [
            heading("Recibimos tu solicitud"), p("Hola, {{ displayName }}:"),
            p("Gracias por registrarte en {{ brandName }}. Un administrador revisará tu solicitud y te avisaremos por correo cuando esté aprobada."),
            details(("Referencia", "{{ reference }}")),
            callout("info", "Si te pedimos confirmar tu correo, hazlo desde el mensaje que te enviamos por separado."), ES_SIG]),
        content("We received your registration", "An administrator will review it soon.", [
            heading("We received your request"), p("Hi {{ displayName }},"),
            p("Thanks for signing up for {{ brandName }}. An administrator will review your request and we will email you once it is approved."),
            details(("Reference", "{{ reference }}")),
            callout("info", "If we asked you to confirm your email, use the separate message we sent you."), EN_SIG])),

    tpl("auth.email-verification", "USER", True, "EmailVerificationRequestedEvent",
        "Single-use link (24 h) that confirms the address belongs to the user.",
        [NAME, v("verifyUrl", "Verification link", "https://app.cronos.mx/auth/verify-email?token=sample", "url"),
         v("expiresAt", "Link expiry, formatted", "11 de octubre de 2026, 10:00")],
        content("Confirma tu correo electrónico", "Un clic y listo.", [
            heading("Confirma tu correo"), p("Hola, {{ displayName }}:"), p("Confirma que esta dirección te pertenece."),
            button("Confirmar correo", "{{ verifyUrl }}"), callout("info", "El enlace vence el {{ expiresAt }} y solo funciona una vez."),
            fallback_es("verifyUrl"), ES_IGNORE, ES_SIG]),
        content("Confirm your email address", "One click and you are done.", [
            heading("Confirm your email"), p("Hi {{ displayName }},"), p("Please confirm that this address belongs to you."),
            button("Confirm email", "{{ verifyUrl }}"), callout("info", "The link expires on {{ expiresAt }} and works only once."),
            fallback_en("verifyUrl"), EN_IGNORE, EN_SIG])),

    tpl("admin.registration-pending", "ADMIN", False, "RegistrationSubmittedEvent",
        "Tells reviewers that a registration waits for a decision.",
        [v("applicantName", "Applicant's name", "Ana López"), v("applicantEmail", "Applicant's email, masked", "a***@cronos.mx"),
         v("reference", "Registration reference", "REG-2026-000042"),
         v("reviewUrl", "Review queue link", "https://app.cronos.mx/cronos/admin/solicitudes-registro", "url")],
        content("Nueva solicitud de registro: {{ applicantName }}", "Hay una cuenta esperando tu revisión.", [
            heading("Solicitud de registro pendiente"),
            p("{{ applicantName }} pidió una cuenta y espera aprobación."),
            details(("Solicitante", "{{ applicantName }}"), ("Correo", "{{ applicantEmail }}"), ("Referencia", "{{ reference }}")),
            button("Revisar solicitudes", "{{ reviewUrl }}"), ES_SIG]),
        content("New registration request: {{ applicantName }}", "An account is waiting for your review.", [
            heading("Registration pending review"),
            p("{{ applicantName }} asked for an account and is waiting for approval."),
            details(("Applicant", "{{ applicantName }}"), ("Email", "{{ applicantEmail }}"), ("Reference", "{{ reference }}")),
            button("Review requests", "{{ reviewUrl }}"), EN_SIG])),

    tpl("auth.account-approved", "USER", True, "RegistrationApprovedEvent",
        "Welcome email once an administrator approves the account.",
        [NAME, v("signInUrl", "Sign-in link", "https://app.cronos.mx/auth/login", "url")],
        content("¡Tu cuenta fue aprobada!", "Ya puedes iniciar sesión.", [
            heading("¡Bienvenido a {{ brandName }}!"), p("Hola, {{ displayName }}:"),
            p("Un administrador aprobó tu cuenta. Ya puedes iniciar sesión con tu usuario o tu correo."),
            button("Iniciar sesión", "{{ signInUrl }}"), ES_SIG]),
        content("Your account was approved!", "You can sign in now.", [
            heading("Welcome to {{ brandName }}!"), p("Hi {{ displayName }},"),
            p("An administrator approved your account. You can now sign in with your username or email."),
            button("Sign in", "{{ signInUrl }}"), EN_SIG])),

    tpl("auth.account-rejected", "USER", True, "RegistrationRejectedEvent",
        "Sent only when the reviewer chose to notify the applicant.",
        [NAME, v("reason", "Localised rejection reason", "Información incompleta"), v("comment", "Reviewer's comment (may be empty)", "Falta el nombre del negocio.")],
        content("Sobre tu solicitud de registro", "No pudimos aprobar tu cuenta.", [
            heading("No pudimos aprobar tu solicitud"), p("Hola, {{ displayName }}:"),
            p("Revisamos tu solicitud de registro y por ahora no podemos aprobarla."),
            details(("Motivo", "{{ reason }}"), ("Comentario", "{{ comment }}")),
            p("Si crees que se trata de un error, escríbenos a {{ supportEmail }}."), ES_SIG]),
        content("About your registration", "We could not approve your account.", [
            heading("We could not approve your request"), p("Hi {{ displayName }},"),
            p("We reviewed your registration request and cannot approve it at this time."),
            details(("Reason", "{{ reason }}"), ("Comment", "{{ comment }}")),
            p("If you think this is a mistake, write to {{ supportEmail }}."), EN_SIG])),

    tpl("auth.password-reset", "USER", True, "PasswordResetRequestedEvent",
        "Single-use password recovery link.",
        [NAME, v("resetUrl", "Reset link", "https://app.cronos.mx/auth/reset-password?token=sample", "url"),
         v("expiresAt", "Link expiry, formatted", "10 de octubre de 2026, 18:30")],
        content("Restablece tu contraseña", "El enlace vence pronto.", [
            heading("Restablecer contraseña"), p("Hola, {{ displayName }}:"),
            p("Recibimos una solicitud para restablecer la contraseña de tu cuenta."),
            button("Elegir nueva contraseña", "{{ resetUrl }}"),
            callout("warning", "El enlace vence el {{ expiresAt }} y solo funciona una vez. Al cambiarla se cerrarán todas tus sesiones."),
            fallback_es("resetUrl"), ES_IGNORE, ES_SIG]),
        content("Reset your password", "The link expires soon.", [
            heading("Reset your password"), p("Hi {{ displayName }},"),
            p("We received a request to reset your account password."),
            button("Choose a new password", "{{ resetUrl }}"),
            callout("warning", "The link expires on {{ expiresAt }} and works only once. Changing it signs you out everywhere."),
            fallback_en("resetUrl"), EN_IGNORE, EN_SIG])),

    tpl("auth.password-changed", "USER", True, "PasswordChangedEvent",
        "Security notice after any password change or reset.",
        [NAME, v("changedAt", "When it changed, formatted", "10 de octubre de 2026, 18:05")],
        content("Tu contraseña cambió", "Aviso de seguridad.", [
            heading("Contraseña actualizada"), p("Hola, {{ displayName }}:"),
            p("La contraseña de tu cuenta cambió el {{ changedAt }}."),
            callout("warning", "Si no fuiste tú, restablécela de inmediato y escríbenos a {{ supportEmail }}."), ES_SIG]),
        content("Your password changed", "Security notice.", [
            heading("Password updated"), p("Hi {{ displayName }},"),
            p("Your account password changed on {{ changedAt }}."),
            callout("warning", "If this wasn't you, reset it right away and write to {{ supportEmail }}."), EN_SIG])),

    tpl("auth.account-locked", "USER", True, "AccountLockedEvent",
        "Too many failed sign-ins locked the account temporarily.",
        [NAME, v("lockedUntil", "End of the lock, formatted", "10 de octubre de 2026, 18:45"),
         v("resetUrl", "Forgot-password page", "https://app.cronos.mx/auth/forgot-password", "url")],
        content("Bloqueamos temporalmente tu cuenta", "Demasiados intentos fallidos.", [
            heading("Cuenta bloqueada temporalmente"), p("Hola, {{ displayName }}:"),
            p("Detectamos varios intentos fallidos de inicio de sesión y bloqueamos tu cuenta hasta el {{ lockedUntil }}."),
            p("Si fuiste tú y olvidaste tu contraseña, puedes restablecerla; eso también desbloquea la cuenta."),
            button("Restablecer contraseña", "{{ resetUrl }}"),
            callout("danger", "Si no fuiste tú, alguien podría estar intentando entrar. Cambia tu contraseña y avísanos a {{ supportEmail }}."), ES_SIG]),
        content("We temporarily locked your account", "Too many failed attempts.", [
            heading("Account temporarily locked"), p("Hi {{ displayName }},"),
            p("We noticed several failed sign-in attempts and locked your account until {{ lockedUntil }}."),
            p("If it was you and you forgot your password, you can reset it; that also unlocks the account."),
            button("Reset password", "{{ resetUrl }}"),
            callout("danger", "If it wasn't you, someone may be trying to get in. Change your password and tell us at {{ supportEmail }}."), EN_SIG])),

    tpl("auth.new-sign-in", "USER", False, "NewDeviceSignInEvent",
        "Sign-in from a device not seen in the last 90 days.",
        [NAME, v("device", "Browser and operating system", "Chrome en Windows"), v("location", "Approximate location", "Ciudad de México"),
         v("ipAddress", "IP address", "201.141.0.10"), v("signedInAt", "When, formatted", "10 de octubre de 2026, 18:00"),
         v("securityUrl", "Sessions page", "https://app.cronos.mx/cronos/cuenta/seguridad", "url")],
        content("Nuevo inicio de sesión en tu cuenta", "¿Fuiste tú?", [
            heading("Nuevo inicio de sesión"), p("Hola, {{ displayName }}:"),
            p("Alguien inició sesión en tu cuenta desde un dispositivo nuevo."),
            details(("Dispositivo", "{{ device }}"), ("Ubicación", "{{ location }}"), ("IP", "{{ ipAddress }}"), ("Fecha", "{{ signedInAt }}")),
            button("Revisar mis sesiones", "{{ securityUrl }}"),
            callout("warning", "Si no fuiste tú, cierra esa sesión y cambia tu contraseña."), ES_SIG]),
        content("New sign-in to your account", "Was it you?", [
            heading("New sign-in"), p("Hi {{ displayName }},"),
            p("Someone signed in to your account from a new device."),
            details(("Device", "{{ device }}"), ("Location", "{{ location }}"), ("IP", "{{ ipAddress }}"), ("When", "{{ signedInAt }}")),
            button("Review my sessions", "{{ securityUrl }}"),
            callout("warning", "If it wasn't you, sign that session out and change your password."), EN_SIG])),

    tpl("quote.sent-to-client", "CLIENT", True, "QuoteSentEvent(channel=EMAIL)",
        "The baker's quote, with the public link, for their client.",
        [v("clientName", "Client's name", "María Pérez"), v("bakerName", "Baker or business name", "Pastelería Luna"),
         v("quoteNumber", "Quote number", "CR-1A2B-202610101800"), v("total", "Total with currency", "$2,450.00 MXN"),
         v("validUntil", "Valid until, formatted", "24 de octubre de 2026"),
         v("quoteUrl", "Public quote link", "https://app.cronos.mx/cronos/cotizaciones/ver/sample", "url")],
        content("{{ bakerName }} te envió la cotización {{ quoteNumber }}", "Revísala y respóndela en línea.", [
            heading("Tu cotización está lista"), p("Hola, {{ clientName }}:"),
            p("{{ bakerName }} preparó una cotización para ti. Puedes revisarla, aceptarla o rechazarla en línea."),
            details(("Cotización", "{{ quoteNumber }}"), ("Total", "{{ total }}"), ("Vigencia", "{{ validUntil }}")),
            button("Ver cotización", "{{ quoteUrl }}"), fallback_es("quoteUrl"), sig("{{ bakerName }}")]),
        content("{{ bakerName }} sent you quote {{ quoteNumber }}", "Review it and reply online.", [
            heading("Your quote is ready"), p("Hi {{ clientName }},"),
            p("{{ bakerName }} prepared a quote for you. You can review, accept or decline it online."),
            details(("Quote", "{{ quoteNumber }}"), ("Total", "{{ total }}"), ("Valid until", "{{ validUntil }}")),
            button("View quote", "{{ quoteUrl }}"), fallback_en("quoteUrl"), sig("{{ bakerName }}")])),

    tpl("quote.accepted-baker", "USER", False, "QuoteAcceptedEvent(actor=CLIENT)",
        "The client accepted from the public link; the baker should schedule the order.",
        [v("bakerName", "Baker's display name", "Ana López"), v("clientName", "Client's name", "María Pérez"),
         v("quoteNumber", "Quote number", "CR-1A2B-202610101800"), v("total", "Total with currency", "$2,450.00 MXN"),
         v("comment", "Client's comment (may be empty)", "¿Pueden entregar a las 4 pm?"),
         v("scheduleUrl", "Link to schedule the order", "https://app.cronos.mx/cronos/cotizaciones/detalles/sample?schedule=1", "url")],
        content("{{ clientName }} aceptó la cotización {{ quoteNumber }}", "Agenda el pedido en tu calendario.", [
            heading("¡Cotización aceptada!"), p("Hola, {{ bakerName }}:"),
            p("{{ clientName }} aceptó tu cotización. Agenda el pedido para no perder la fecha."),
            details(("Cotización", "{{ quoteNumber }}"), ("Total", "{{ total }}"), ("Comentario", "{{ comment }}")),
            button("Agendar pedido", "{{ scheduleUrl }}"), ES_SIG]),
        content("{{ clientName }} accepted quote {{ quoteNumber }}", "Schedule the order in your calendar.", [
            heading("Quote accepted!"), p("Hi {{ bakerName }},"),
            p("{{ clientName }} accepted your quote. Schedule the order so the date is not missed."),
            details(("Quote", "{{ quoteNumber }}"), ("Total", "{{ total }}"), ("Comment", "{{ comment }}")),
            button("Schedule order", "{{ scheduleUrl }}"), EN_SIG])),

    tpl("quote.accepted-client", "CLIENT", True, "QuoteAcceptedEvent (client has email)",
        "Confirms to the client that the quote was accepted.",
        [v("clientName", "Client's name", "María Pérez"), v("bakerName", "Baker or business name", "Pastelería Luna"),
         v("quoteNumber", "Quote number", "CR-1A2B-202610101800"), v("total", "Total with currency", "$2,450.00 MXN"),
         v("eventDate", "Event date, formatted (may be empty)", "24 de octubre de 2026")],
        content("Confirmación de tu cotización {{ quoteNumber }}", "Tu pedido está confirmado.", [
            heading("¡Gracias por tu confirmación!"), p("Hola, {{ clientName }}:"),
            p("La cotización {{ quoteNumber }} de {{ bakerName }} quedó aceptada."),
            details(("Total", "{{ total }}"), ("Fecha del evento", "{{ eventDate }}")),
            p("{{ bakerName }} se pondrá en contacto contigo para los detalles de la entrega."), sig("{{ bakerName }}")]),
        content("Your quote {{ quoteNumber }} is confirmed", "Your order is confirmed.", [
            heading("Thank you for confirming!"), p("Hi {{ clientName }},"),
            p("Quote {{ quoteNumber }} from {{ bakerName }} is now accepted."),
            details(("Total", "{{ total }}"), ("Event date", "{{ eventDate }}")),
            p("{{ bakerName }} will contact you about the delivery details."), sig("{{ bakerName }}")])),

    tpl("quote.expiring-reminder", "USER", False, "QuoteExpiringEvent",
        "An open quote expires in two days or less.",
        [v("bakerName", "Baker's display name", "Ana López"), v("clientName", "Client's name", "María Pérez"),
         v("quoteNumber", "Quote number", "CR-1A2B-202610101800"), v("validUntil", "Expiry, formatted", "12 de octubre de 2026"),
         v("quoteUrl", "Quote detail link", "https://app.cronos.mx/cronos/cotizaciones/detalles/sample", "url")],
        content("La cotización {{ quoteNumber }} está por vencer", "Dale seguimiento a {{ clientName }}.", [
            heading("Cotización por vencer"), p("Hola, {{ bakerName }}:"),
            p("La cotización {{ quoteNumber }} para {{ clientName }} vence el {{ validUntil }} y aún no tiene respuesta."),
            button("Ver cotización", "{{ quoteUrl }}"), ES_SIG]),
        content("Quote {{ quoteNumber }} is about to expire", "Follow up with {{ clientName }}.", [
            heading("Quote about to expire"), p("Hi {{ bakerName }},"),
            p("Quote {{ quoteNumber }} for {{ clientName }} expires on {{ validUntil }} and has no answer yet."),
            button("View quote", "{{ quoteUrl }}"), EN_SIG])),

    tpl("calendar.order-reminder", "USER", False, "CalendarReminderDueEvent",
        "Reminder before an order, with the production sheet link.",
        [NAME, v("title", "Event title", "CR-1A2B · María Pérez"), v("startsAt", "Start, formatted", "24 de octubre de 2026, 16:00"),
         v("location", "Location (may be empty)", "Av. Reforma 100, CDMX"), v("clientName", "Client (may be empty)", "María Pérez"),
         v("productionUrl", "Production sheet link", "https://app.cronos.mx/cronos/calendario/eventos/sample/produccion", "url")],
        content("Recordatorio: {{ title }}", "{{ startsAt }}", [
            heading("Tienes un pedido próximo"), p("Hola, {{ displayName }}:"),
            details(("Pedido", "{{ title }}"), ("Fecha", "{{ startsAt }}"), ("Cliente", "{{ clientName }}"), ("Lugar", "{{ location }}")),
            button("Abrir hoja de producción", "{{ productionUrl }}"), ES_SIG]),
        content("Reminder: {{ title }}", "{{ startsAt }}", [
            heading("You have an upcoming order"), p("Hi {{ displayName }},"),
            details(("Order", "{{ title }}"), ("When", "{{ startsAt }}"), ("Client", "{{ clientName }}"), ("Location", "{{ location }}")),
            button("Open production sheet", "{{ productionUrl }}"), EN_SIG])),

    tpl("calendar.order-scheduled-client", "CLIENT", True, "OrderScheduledEvent (client has email)",
        "Tells the client the date and place of their order.",
        [v("clientName", "Client's name", "María Pérez"), v("bakerName", "Baker or business name", "Pastelería Luna"),
         v("quoteNumber", "Quote number (may be empty)", "CR-1A2B-202610101800"), v("startsAt", "Delivery or pickup, formatted", "24 de octubre de 2026, 16:00"),
         v("location", "Location (may be empty)", "Av. Reforma 100, CDMX"), v("kind", "Delivery or pickup, localised", "Entrega a domicilio")],
        content("Tu pedido quedó agendado", "{{ startsAt }}", [
            heading("Pedido agendado"), p("Hola, {{ clientName }}:"),
            p("{{ bakerName }} agendó tu pedido."),
            details(("Tipo", "{{ kind }}"), ("Fecha", "{{ startsAt }}"), ("Lugar", "{{ location }}"), ("Cotización", "{{ quoteNumber }}")),
            sig("{{ bakerName }}")]),
        content("Your order is scheduled", "{{ startsAt }}", [
            heading("Order scheduled"), p("Hi {{ clientName }},"),
            p("{{ bakerName }} scheduled your order."),
            details(("Type", "{{ kind }}"), ("When", "{{ startsAt }}"), ("Location", "{{ location }}"), ("Quote", "{{ quoteNumber }}")),
            sig("{{ bakerName }}")])),

    tpl("auth.invitation", "USER", True, "UserInvitedEvent",
        "An administrator created the account; the link sets the first password.",
        [NAME, v("activationUrl", "Activation link", "https://app.cronos.mx/auth/activate?token=sample", "url"),
         v("expiresAt", "Link expiry, formatted", "13 de octubre de 2026, 18:00")],
        content("Te invitaron a {{ brandName }}", "Activa tu cuenta.", [
            heading("Activa tu cuenta"), p("Hola, {{ displayName }}:"),
            p("Se creó una cuenta para ti. Define tu contraseña para empezar."),
            button("Activar cuenta", "{{ activationUrl }}"), callout("info", "El enlace vence el {{ expiresAt }}."),
            fallback_es("activationUrl"), ES_SIG]),
        content("You were invited to {{ brandName }}", "Activate your account.", [
            heading("Activate your account"), p("Hi {{ displayName }},"),
            p("An account was created for you. Choose your password to get started."),
            button("Activate account", "{{ activationUrl }}"), callout("info", "The link expires on {{ expiresAt }}."),
            fallback_en("activationUrl"), EN_SIG])),

    tpl("auth.temporary-password", "USER", True, "TemporaryPasswordIssuedEvent",
        "An administrator assigned a temporary password that must be changed at sign-in.",
        [NAME, v("temporaryPassword", "The temporary password", "Tmp-7g4K-92q"),
         v("signInUrl", "Sign-in link", "https://app.cronos.mx/auth/login", "url")],
        content("Tu contraseña temporal de {{ brandName }}", "Cámbiala al iniciar sesión.", [
            heading("Contraseña temporal"), p("Hola, {{ displayName }}:"),
            p("Un administrador te asignó una contraseña temporal. Deberás cambiarla al iniciar sesión."),
            code("{{ temporaryPassword }}"), button("Iniciar sesión", "{{ signInUrl }}"), ES_IGNORE, ES_SIG]),
        content("Your temporary {{ brandName }} password", "Change it when you sign in.", [
            heading("Temporary password"), p("Hi {{ displayName }},"),
            p("An administrator assigned you a temporary password. You will have to change it when you sign in."),
            code("{{ temporaryPassword }}"), button("Sign in", "{{ signInUrl }}"), EN_IGNORE, EN_SIG])),

    tpl("auth.email-changed", "USER", True, "EmailChangedEvent",
        "Notice to the previous address that the account email changed.",
        [NAME, v("newEmail", "New address, masked", "a***@cronos.mx")],
        content("Tu correo electrónico cambió", "Aviso de seguridad.", [
            heading("Correo actualizado"), p("Hola, {{ displayName }}:"),
            p("El correo de tu cuenta se cambió a {{ newEmail }}."),
            callout("warning", "Si no fuiste tú, avisa a un administrador o escríbenos a {{ supportEmail }}."), ES_SIG]),
        content("Your email address changed", "Security notice.", [
            heading("Email updated"), p("Hi {{ displayName }},"),
            p("Your account email was changed to {{ newEmail }}."),
            callout("warning", "If this wasn't you, tell an administrator or write to {{ supportEmail }}."), EN_SIG])),

    tpl("auth.two-factor-disabled", "USER", True, "TwoFactorDisabledEvent",
        "Two-step verification was turned off and other sessions were closed.",
        [NAME],
        content("Desactivaste la verificación en dos pasos", "Aviso de seguridad.", [
            heading("Verificación en dos pasos desactivada"), p("Hola, {{ displayName }}:"),
            p("La verificación en dos pasos de tu cuenta se desactivó y se cerraron tus otras sesiones."),
            callout("warning", "Si no fuiste tú, avisa a un administrador."), ES_SIG]),
        content("You turned off two-step verification", "Security notice.", [
            heading("Two-step verification turned off"), p("Hi {{ displayName }},"),
            p("Two-step verification was turned off for your account and your other sessions were closed."),
            callout("warning", "If this wasn't you, tell an administrator."), EN_SIG])),

    tpl("auth.recovery-code-used", "USER", True, "RecoveryCodeUsedEvent",
        "Someone signed in with a recovery code.",
        [NAME, v("remainingCodes", "Recovery codes left", "7")],
        content("Se usó un código de respaldo", "Aviso de seguridad.", [
            heading("Inicio de sesión con código de respaldo"), p("Hola, {{ displayName }}:"),
            p("Alguien inició sesión en tu cuenta con un código de respaldo. Te quedan {{ remainingCodes }}."),
            callout("warning", "Si no fuiste tú, cambia tu contraseña y avisa a un administrador."), ES_SIG]),
        content("A recovery code was used", "Security notice.", [
            heading("Sign-in with a recovery code"), p("Hi {{ displayName }},"),
            p("Someone signed in to your account with a recovery code. You have {{ remainingCodes }} left."),
            callout("warning", "If this wasn't you, change your password and tell an administrator."), EN_SIG])),

    tpl("auth.registration-attempt", "USER", True, "RegistrationAttemptOnExistingEmailEvent",
        "Someone tried to register with an address that already has an account (never revealed to them).",
        [NAME, v("signInUrl", "Sign-in link", "https://app.cronos.mx/auth/login", "url"),
         v("resetUrl", "Forgot-password page", "https://app.cronos.mx/auth/forgot-password", "url")],
        content("Alguien intentó registrarse con tu correo", "Ya tienes una cuenta.", [
            heading("Ya tienes una cuenta"), p("Hola, {{ displayName }}:"),
            p("Alguien intentó crear una cuenta nueva con tu correo. No creamos ninguna cuenta."),
            p("Si fuiste tú, inicia sesión con tu cuenta actual o restablece tu contraseña."),
            button("Iniciar sesión", "{{ signInUrl }}"), p("¿Olvidaste tu contraseña? {{ resetUrl }}"), ES_IGNORE, ES_SIG]),
        content("Someone tried to register with your email", "You already have an account.", [
            heading("You already have an account"), p("Hi {{ displayName }},"),
            p("Someone tried to create a new account with your email. No account was created."),
            p("If it was you, sign in with your existing account or reset your password."),
            button("Sign in", "{{ signInUrl }}"), p("Forgot your password? {{ resetUrl }}"), EN_IGNORE, EN_SIG])),

    tpl("recipe.shared", "CLIENT", True, "RecipeSharedEvent",
        "A recipe was shared with an email recipient.",
        [v("senderName", "Who shared it", "Ana López"), v("recipeName", "Recipe name", "Pastel de tres leches"),
         v("shareUrl", "Public recipe link", "https://app.cronos.mx/shared/recipe/sample", "url"),
         v("expirationDays", "Days the link stays valid", "7")],
        content("{{ senderName }} compartió una receta contigo", "{{ recipeName }}", [
            heading("Te compartieron una receta"),
            p("{{ senderName }} compartió la receta «{{ recipeName }}» contigo."),
            button("Ver receta", "{{ shareUrl }}"), callout("info", "El enlace está disponible durante {{ expirationDays }} días."),
            fallback_es("shareUrl"), ES_SIG]),
        content("{{ senderName }} shared a recipe with you", "{{ recipeName }}", [
            heading("A recipe was shared with you"),
            p("{{ senderName }} shared the recipe “{{ recipeName }}” with you."),
            button("View recipe", "{{ shareUrl }}"), callout("info", "The link is available for {{ expirationDays }} days."),
            fallback_en("shareUrl"), EN_SIG])),
]

catalog = {"version": 1, "templates": TEMPLATES}
target = pathlib.Path(__file__).resolve().parents[2] / "src/main/resources/mail/catalog.json"
target.parent.mkdir(parents=True, exist_ok=True)
target.write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(f"{len(TEMPLATES)} templates -> {target}")
