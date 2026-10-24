{
  description = "Kai 9000 - AI assistant (Compose Desktop, prebuilt AppImage)";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";

  outputs =
    { self, nixpkgs }:
    let
      system = "x86_64-linux";
      pkgs = nixpkgs.legacyPackages.${system};
    in
    {
      packages.${system} = {
        kai = pkgs.callPackage ./package.nix { };
        default = self.packages.${system}.kai;
      };

      overlays.default = final: _prev: {
        kai = final.callPackage ./package.nix { };
      };

      apps.${system}.default = {
        type = "app";
        program = "${self.packages.${system}.kai}/bin/kai";
      };
    };
}
