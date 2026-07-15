import SwiftUI

/// Email+password login/registration against the backend /auth endpoints.
struct LoginView: View {
    let error: String?
    let onLogin: (_ email: String, _ password: String) -> Void
    let onRegister: (_ username: String, _ email: String, _ password: String) -> Void

    @State private var registering = false
    @State private var username = ""
    @State private var email = ""
    @State private var password = ""

    var body: some View {
        VStack(spacing: 12) {
            Text("UWB Peer Compass").font(.title).bold()
            if registering {
                TextField("Username", text: $username).textFieldStyle(.roundedBorder).autocapitalization(.none)
            }
            TextField("Email", text: $email).textFieldStyle(.roundedBorder).keyboardType(.emailAddress).autocapitalization(.none)
            SecureField("Password", text: $password).textFieldStyle(.roundedBorder)
            if let error { Text(error).foregroundColor(.red).font(.caption) }
            Button(registering ? "Create account" : "Log in") {
                if registering { onRegister(username, email, password) } else { onLogin(email, password) }
            }
            .buttonStyle(.borderedProminent)
            Button(registering ? "Have an account? Log in" : "New here? Create an account") {
                registering.toggle()
            }
            .font(.caption)
        }
        .padding(24)
    }
}
