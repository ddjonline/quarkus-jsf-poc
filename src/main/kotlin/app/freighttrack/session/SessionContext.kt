package app.freighttrack.session

import jakarta.enterprise.context.RequestScoped

@RequestScoped
open class SessionContext {
    var id: String = ""
    var isNew: Boolean = false
}
