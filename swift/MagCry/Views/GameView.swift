import SwiftUI

struct GameView: View {
    @Bindable var vm: GameViewModel

    var body: some View {
        ZStack(alignment: .bottom) {
            // Main game content
            VStack(spacing: 0) {
                // Header: Phase + Player info
                header
                    .padding(.horizontal)
                    .padding(.top, 8)

                // Central cards
                CentralCardsView(
                    centralCards: vm.gameState?.centralCards ?? [],
                    revealed: vm.revealedCentralCards
                )
                .padding(.vertical, 12)

                Divider().background(Color.white.opacity(0.2))

                // Last event banner
                lastEventBanner
                    .padding(.horizontal)
                    .padding(.vertical, 8)

                Divider().background(Color.white.opacity(0.2))

                // Main interaction area (expands to fill)
                interactionArea
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .padding(.horizontal)
                    .padding(.vertical, 12)

                // Bottom bar — pinned at bottom
                bottomBar
                    .padding(.horizontal)
                    .padding(.bottom, 8)
            }

            // Tutorial coach overlay — pinned to bottom
            if let tutorial = vm.tutorialManager, tutorial.isActive {
                TutorialCoachView(manager: tutorial)
                    .padding(.bottom, 60)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.clear)
        .sheet(isPresented: $vm.showHistory) {
            TradeLogView(entries: vm.log)
        }
        .alert("Quit this game?", isPresented: $vm.showQuitAlert) {
            Button("Cancel", role: .cancel) { }
            Button("Quit", role: .destructive) {
                vm.quitGame()
            }
        }
    }

    // MARK: - Header

    private var header: some View {
        VStack(spacing: 4) {
            HStack {
                Text(vm.currentPhase.label)
                    .font(.headline)
                    .foregroundStyle(.white)
                Spacer()
                Text("\(vm.tradeCount) trades")
                    .font(.caption)
                    .foregroundStyle(.secondary)

                // Quit button
                Button {
                    vm.showQuitAlert = true
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .font(.title3)
                        .foregroundStyle(.white.opacity(0.4))
                }
                .padding(.leading, 8)
            }

            HStack {
                Label("Your card: \(vm.playerCard >= 0 ? "+" : "")\(vm.playerCard)",
                      systemImage: "suit.spade.fill")
                    .font(.subheadline)
                    .foregroundStyle(.yellow)
                Spacer()
                Text("EV: \(vm.playerEV, specifier: "%.1f")")
                    .font(.subheadline.monospacedDigit())
                    .foregroundStyle(.cyan)
            }
        }
    }

    // MARK: - Last Event Banner

    private var lastEventBanner: some View {
        Text(vm.lastEvent)
            .font(.subheadline)
            .foregroundStyle(.white.opacity(0.9))
            .frame(maxWidth: .infinity, alignment: .leading)
            .lineLimit(2)
    }

    // MARK: - Interaction Area

    @ViewBuilder
    private var interactionArea: some View {
        switch vm.playingState {
        case .botAsksYou(let botName):
            QuoteInputView(
                vm: vm,
                botName: botName,
                lockedBid: vm.isTutorial ? 66 : nil
            )

        case .botDecided(let botName, let action):
            VStack(spacing: 8) {
                Text(botName)
                    .font(.headline)
                    .foregroundStyle(.white)
                Text(action)
                    .font(.title3)
                    .foregroundStyle(action.contains("buys") || action.contains("sells")
                        ? .yellow : .secondary)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)

        case .playerTurn, .windDownTurn:
            playerTradingArea

        case .botsTrading:
            VStack(spacing: 8) {
                ProgressView()
                    .tint(.white)
                Text("Traders are trading...")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)

        case .idle:
            Color.clear
        }
    }

    // MARK: - Player Trading Area

    private var playerTradingArea: some View {
        VStack(spacing: 16) {
            Spacer()

            if let active = vm.activeQuote {
                // Single bot quote with Buy / Sell / Pass
                // During tutorial, restrict which buttons are enabled
                BotQuoteView(
                    botName: active.botName,
                    quote: active.quote,
                    onBuy: { vm.buyFromActive() },
                    onSell: { vm.sellToActive() },
                    onPass: { vm.passOnQuote() },
                    buyEnabled: vm.tutorialManager?.currentStep?.canBuy ?? true,
                    sellEnabled: vm.tutorialManager?.currentStep?.canSell ?? true,
                    passEnabled: vm.tutorialManager?.currentStep?.canPass ?? true
                )
            } else if let result = vm.lastActionResult {
                // Brief result message (auto-clears)
                Text(result)
                    .font(.headline)
                    .foregroundStyle(.yellow)
                    .transition(.opacity)
            } else {
                // 4 bot buttons — ask for a price
                botAskButtons
            }

            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .animation(.easeInOut(duration: 0.2), value: vm.activeQuote != nil)
    }

    // MARK: - Bot Ask Buttons

    private var botAskButtons: some View {
        let allowedBots = vm.tutorialManager?.currentStep?.allowedBotNames

        return VStack(spacing: 10) {
            // 2x2 grid of ask buttons
            HStack(spacing: 10) {
                ForEach(GameViewModel.botNames.prefix(2), id: \.self) { name in
                    askButton(for: name, enabled: allowedBots == nil || allowedBots!.contains(name))
                }
            }
            HStack(spacing: 10) {
                ForEach(GameViewModel.botNames.suffix(2), id: \.self) { name in
                    askButton(for: name, enabled: allowedBots == nil || allowedBots!.contains(name))
                }
            }
        }
    }

    private func askButton(for name: String, enabled: Bool) -> some View {
        Button {
            vm.askBot(name)
        } label: {
            Text("Ask \(name)")
                .font(.subheadline)
                .fontWeight(.medium)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
                .background(enabled ? Color.blue.opacity(0.3) : Color.gray.opacity(0.15))
                .foregroundStyle(enabled ? .white : .white.opacity(0.3))
                .clipShape(RoundedRectangle(cornerRadius: 10))
        }
        .disabled(!enabled)
    }

    // MARK: - Bottom Bar

    private var bottomBar: some View {
        HStack {
            if vm.isWindDownTurn {
                Button("Continue") {
                    vm.windDownContinue()
                }
                .buttonStyle(PrimaryButtonStyle(color: .orange))
            } else if case .playerTurn = vm.playingState {
                let nextEnabled = vm.tutorialManager?.currentStep?.canNext ?? true
                Button("Next") {
                    vm.playerTappedNext()
                }
                .buttonStyle(PrimaryButtonStyle(color: nextEnabled ? .green : .gray))
                .disabled(!nextEnabled)
            } else {
                Spacer()
            }

            // History button
            Button {
                vm.showHistory = true
            } label: {
                Image(systemName: "clock.arrow.circlepath")
                    .font(.title3)
                    .foregroundStyle(.secondary)
                    .padding(10)
                    .background(Color.white.opacity(0.08))
                    .clipShape(Circle())
            }
        }
        .frame(height: 44)
    }
}

// MARK: - Primary Button Style

struct PrimaryButtonStyle: ButtonStyle {
    let color: Color

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.headline)
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 12)
            .background(color.opacity(configuration.isPressed ? 0.5 : 0.7))
            .clipShape(RoundedRectangle(cornerRadius: 12))
    }
}
