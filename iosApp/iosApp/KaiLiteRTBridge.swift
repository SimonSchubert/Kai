//
//  KaiLiteRTBridge.swift
//  iosApp
//
//  Implementation of the Kotlin `LiteRTSwiftBridge` protocol. Wraps the official
//  LiteRT-LM Swift SDK (SPM: https://github.com/google-ai-edge/LiteRT-LM) so the
//  Kotlin/Native side can drive local Gemma 4 inference through Obj-C interop.
//

import Foundation
import ComposeApp
import LiteRTLM

@objc public class KaiLiteRTBridge: NSObject, LiteRTSwiftBridge {

    private var engine: Engine?

    public func initializeEngine(
        modelPath: String,
        cacheDir: String,
        maxNumTokens: Int32,
        onComplete: @escaping (String?) -> Void
    ) {
        Task { [weak self] in
            let maxTokens: Int? = maxNumTokens > 0 ? Int(maxNumTokens) : nil
            // GPU init can fail on older devices or when memory is tight; retry on CPU.
            do {
                let newEngine = try await Self.tryInit(modelPath: modelPath, cacheDir: cacheDir, maxNumTokens: maxTokens, backend: .gpu)
                await MainActor.run {
                    self?.engine = newEngine
                    onComplete(nil)
                }
            } catch let gpuError {
                do {
                    let newEngine = try await Self.tryInit(modelPath: modelPath, cacheDir: cacheDir, maxNumTokens: maxTokens, backend: .cpu())
                    await MainActor.run {
                        self?.engine = newEngine
                        onComplete(nil)
                    }
                } catch let cpuError {
                    await MainActor.run {
                        onComplete("GPU init failed: \(gpuError.localizedDescription); CPU fallback failed: \(cpuError.localizedDescription)")
                    }
                }
            }
        }
    }

    private static func tryInit(modelPath: String, cacheDir: String, maxNumTokens: Int?, backend: Backend) async throws -> Engine {
        let config = try EngineConfig(modelPath: modelPath, backend: backend, maxNumTokens: maxNumTokens, cacheDir: cacheDir)
        let engine = Engine(engineConfig: config)
        try await engine.initialize()
        return engine
    }

    public func releaseEngine() {
        // Engine is a Swift actor; nulling the reference drops the retain count and lets
        // deinit release the native handle.
        engine = nil
    }

    public func isEngineReady() -> Bool {
        return engine != nil
    }

    public func chat(
        messagesJson: String,
        systemPrompt: String?,
        onResult: @escaping (String?, String?) -> Void
    ) {
        guard let engine = self.engine else {
            onResult(nil, "Engine not initialized")
            return
        }

        Task {
            do {
                let messages = try Self.parseMessages(messagesJson)
                guard let lastUserIndex = messages.lastIndex(where: { $0.role == .user }) else {
                    await MainActor.run { onResult(nil, "No user message in history") }
                    return
                }

                let history = Array(messages.prefix(lastUserIndex))
                let lastMessage = messages[lastUserIndex]

                let samplerConfig = try SamplerConfig(topK: 40, topP: 0.95, temperature: 0.8)
                let convConfig = ConversationConfig(
                    systemMessage: systemPrompt.map { Message($0, role: .system) },
                    initialMessages: history,
                    samplerConfig: samplerConfig
                )

                let conversation = try await engine.createConversation(with: convConfig)
                let response = try await conversation.sendMessage(lastMessage)

                let text = Self.extractText(from: response)
                await MainActor.run { onResult(text, nil) }
            } catch {
                await MainActor.run { onResult(nil, error.localizedDescription) }
            }
        }
    }

    private static func parseMessages(_ json: String) throws -> [Message] {
        guard let data = json.data(using: .utf8) else {
            throw NSError(domain: "KaiLiteRTBridge", code: 1, userInfo: [NSLocalizedDescriptionKey: "Invalid UTF-8 in messages JSON"])
        }
        let raw = try JSONSerialization.jsonObject(with: data) as? [[String: String]] ?? []
        return raw.map { entry in
            let roleStr = entry["role"] ?? "user"
            let role: Role = (roleStr == "model" || roleStr == "assistant") ? .model : .user
            return Message(entry["content"] ?? "", role: role)
        }
    }

    private static func extractText(from message: Message) -> String {
        for content in message.contents {
            if case .text(let value) = content {
                return value
            }
        }
        return ""
    }
}

@objc public class KaiLiteRTBridgeInstaller: NSObject {
    @objc public static func install() {
        LiteRTBridgeRegistry.shared.bridge = KaiLiteRTBridge()
    }
}
