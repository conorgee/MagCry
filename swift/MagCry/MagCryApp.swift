import SwiftUI

@main
struct MagCryApp: App {
    @State private var vm = GameViewModel()

    var body: some Scene {
        WindowGroup {
            RootView(vm: vm)
                .preferredColorScheme(.dark)
        }
    }
}

struct RootView: View {
    var vm: GameViewModel

    var body: some View {
        ZStack {
            Color(white: 0.08)
                .ignoresSafeArea()

            switch vm.screen {
            case .mainMenu:
                MainMenuView(vm: vm)
            case .playing:
                GameView(vm: vm)
            case .settlement:
                SettlementView(vm: vm)
            }
        }
    }
}
