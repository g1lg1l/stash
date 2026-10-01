import SwiftData
import SwiftUI

/// First launch only. An account is optional: Not Now goes straight to Home.
struct WelcomeView: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            VStack(spacing: Spacing.m) {
                Spacer()
                Image(systemName: "tray.and.arrow.down.fill")
                    .font(.system(size: 64))
                    .foregroundStyle(.tint)
                Text("Share it now. Find it later.")
                    .font(.largeTitle.bold())
                    .multilineTextAlignment(.center)
                Text("Share a link from any app to Stash. It fills in the title and picture, files it by topic, and brings it back when you've forgotten about it.")
                    .multilineTextAlignment(.center)
                    .foregroundStyle(.secondary)
                Text("Sign in to sync your saves. Stash works fully without an account too.")
                    .font(.subheadline)
                    .multilineTextAlignment(.center)
                    .foregroundStyle(.secondary)
                Spacer()
                NavigationLink {
                    AuthView(creating: false)
                } label: {
                    Text("Sign In").frame(maxWidth: .infinity)
                }
                .buttonStyle(.glassProminent)
                NavigationLink {
                    AuthView(creating: true)
                } label: {
                    Text("Create Account").frame(maxWidth: .infinity)
                }
                .buttonStyle(.glass)
                Button("Not Now") { dismiss() }
                    .padding(.top, Spacing.xs)
            }
            .controlSize(.large)
            .padding(Spacing.l)
        }
        .onChange(of: Account.shared.isSignedIn) { _, signedIn in
            if signedIn { dismiss() }
        }
    }
}

/// Email and password, switching between Sign In and Create Account.
struct AuthView: View {
    @State var creating: Bool
    @State private var email = ""
    @State private var password = ""
    @State private var working = false
    @State private var message: String?
    @Environment(\.modelContext) private var modelContext
    @Environment(\.dismiss) private var dismiss

    private var canSubmit: Bool { !working && email.contains("@") && password.count >= 6 }

    var body: some View {
        Form {
            Picker("Mode", selection: $creating) {
                Text("Sign In").tag(false)
                Text("Create Account").tag(true)
            }
            .pickerStyle(.segmented)
            .labelsHidden()
            .listRowBackground(Color.clear)
            .listRowInsets(EdgeInsets())

            Section {
                TextField("Email", text: $email)
                    .textContentType(.username)
                    .keyboardType(.emailAddress)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                SecureField("Password", text: $password)
                    .textContentType(creating ? .newPassword : .password)
            } footer: {
                if let message {
                    Text(message)
                } else if creating {
                    Text("At least 6 characters.")
                }
            }

            Section {
                Button {
                    Task { await submit() }
                } label: {
                    HStack {
                        Text(creating ? "Create Account" : "Sign In")
                        if working { Spacer(); ProgressView() }
                    }
                }
                .disabled(!canSubmit)
            }
        }
        .onSubmit { Task { await submit() } }
        .navigationTitle(creating ? "Create Account" : "Sign In")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func submit() async {
        guard canSubmit else { return }
        working = true
        defer { working = false }
        do {
            let email = email.trimmingCharacters(in: .whitespaces)
            if let notice = try await Account.shared.signIn(email: email, password: password, creating: creating) {
                message = notice
                creating = false
            } else {
                Task { await Account.shared.sync(modelContext) }
                dismiss()
            }
        } catch {
            message = error.localizedDescription
        }
    }
}

#Preview {
    NavigationStack { AuthView(creating: false) }
}
