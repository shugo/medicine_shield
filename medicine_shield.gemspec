# coding: utf-8
lib = File.expand_path('../lib', __FILE__)
$LOAD_PATH.unshift(lib) unless $LOAD_PATH.include?(lib)
require 'medicine_shield/version'

Gem::Specification.new do |spec|
  spec.name          = "medicine_shield"
  spec.version       = MedicineShield::VERSION
  spec.authors       = ["Shugo Maeda"]
  spec.email         = ["shugo@ruby-lang.org"]

  spec.summary       = "A Mastodon client for Textbringer."
  spec.description   = "A Mastodon client for Textbringer."
  spec.homepage      = "https://github.com/shugo/medicine_shield"
  spec.license       = "MIT"

  spec.files         = `git ls-files -z`.split("\x0").reject do |f|
    f.match(%r{^(test|spec|features)/})
  end
  spec.bindir        = "exe"
  spec.executables   = spec.files.grep(%r{^exe/}) { |f| File.basename(f) }
  spec.require_paths = ["lib"]

  spec.add_runtime_dependency "textbringer"
  spec.add_runtime_dependency "mastodon-api"
  spec.add_runtime_dependency "oauth2"

  spec.add_development_dependency "bundler"
  spec.add_development_dependency "rake"
end
