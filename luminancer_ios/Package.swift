// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "LumiSynth",
    platforms: [
        .iOS(.v15)
    ],
    products: [
        .library(
            name: "LumiSynthCore",
            targets: ["LumiSynthCore"]
        )
    ],
    targets: [
        .target(
            name: "LumiSynthCore",
            path: ".",
            sources: [
                "App",
                "Core",
                "Features"
            ],
            resources: [
                .process("Core/Renderer/Shaders.metal")
            ],
            cSettings: [
                .headerSearchPath("Core/Renderer")
            ]
        )
    ]
)
