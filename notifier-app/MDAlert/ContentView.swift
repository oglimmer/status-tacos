//
//  ContentView.swift
//  MDAlert
//
//  Created by Oliver Zimpasser on 6/26/25.
//

import SwiftUI

struct ContentView: View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject var authService: AuthenticationService

    var body: some View {
        VStack {
            HStack {
                Spacer()
                Button(action: {
                    if let appDelegate = NSApp.delegate as? AppDelegate {
                        appDelegate.popover.performClose(nil)
                    }
                }) {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundColor(.secondary)
                }
                .buttonStyle(PlainButtonStyle())
                .padding(.trailing, 8)
                .padding(.top, 8)
            }

            Spacer()

            if authService.isAuthenticated {
                UserInfoView(userInfo: authService.userInfo, onLogout: {
                    authService.logout()
                })
            } else {
                LoginView(authService: authService)
            }

            Spacer()

            Button("Quit Application") {
                NSApplication.shared.terminate(nil)
            }
            .padding(.bottom, 20)
        }
        .padding()
        .frame(width: 400, height: 300)
        .onAppear {
            // ContentView now receives the AuthenticationService from AppDelegate
        }
    }
}

struct LoginView: View {
    @ObservedObject var authService: AuthenticationService

    var body: some View {
        VStack(spacing: 20) {
            Image(systemName: "person.circle")
                .imageScale(.large)
                .foregroundStyle(.tint)
                .font(.system(size: 48))

            Text("Welcome to MDAlert")
                .font(.title2)
                .fontWeight(.semibold)

            Text("Sign in with your account to continue")
                .font(.body)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)

            if let errorMessage = authService.errorMessage {
                Text(errorMessage)
                    .foregroundColor(.red)
                    .font(.caption)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal)
            }

            Button(action: {
                authService.login()
            }) {
                HStack {
                    if authService.isLoading {
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: .white))
                            .scaleEffect(0.8)
                    } else {
                        Image(systemName: "arrow.right.circle.fill")
                    }
                    Text(authService.isLoading ? "Signing In..." : "Sign In")
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 8)
            }
            .buttonStyle(.borderedProminent)
            .disabled(authService.isLoading)
        }
        .padding()
    }
}

struct UserInfoView: View {
    let userInfo: UserInfo?
    let onLogout: () -> Void

    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "person.crop.circle.fill")
                .imageScale(.large)
                .foregroundStyle(.green)
                .font(.system(size: 48))

            VStack(spacing: 8) {
                if let name = userInfo?.name {
                    Text(name)
                        .font(.title2)
                        .fontWeight(.semibold)
                }

                if let email = userInfo?.email {
                    Text(email)
                        .font(.body)
                        .foregroundColor(.secondary)
                }

                if let username = userInfo?.preferredUsername {
                    Text("@\(username)")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }

                if let subject = userInfo?.sub {
                    Text("ID: \(subject)")
                        .font(.caption2)
//                        .foregroundColor(.tertiary)
                        .lineLimit(1)
                        .truncationMode(.middle)
                }
            }

            Button("Sign Out") {
                onLogout()
            }
            .buttonStyle(.bordered)
        }
        .padding()
    }
}

#Preview {
    ContentView(authService: AuthenticationService.shared)
}
