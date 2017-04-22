# frozen_string_literal: true

require "mastodon"
require "oauth2"
require "fileutils"

module MedicineShield
  APP_NAME = "medicine_shield"
  SCOPE = "read write follow"

  module_function

  def config_path
    File.expand_path("config.json", CONFIG[:medicine_shield_directory])
  end

  def get_config
    path = config_path
    begin
      JSON.load(File.read(path))
    rescue Errno::ENOENT
      config = create_config
      FileUtils.mkdir_p(File.dirname(path))
      old_umask = File.umask
      File.umask(0066)
      begin
        File.write(path, config.to_json)
      ensure
        File.umask(old_umask)
      end
      config
    end
  end

  def create_config
    url = read_from_minibuffer("Mastodon URL: ",
                               default: "https://mstdn.sanin.club")
    mstdn_client = Mastodon::REST::Client.new(base_url: url)
    app = mstdn_client.create_app(APP_NAME, "urn:ietf:wg:oauth:2.0:oob", SCOPE)
    oauth_client = OAuth2::Client.new(app.client_id,
                                      app.client_secret,
                                      site: url)
    email = read_from_minibuffer("Mastodon user e-mail: ")
    password = read_from_minibuffer("Mastodon password: ")
    token = oauth_client.password.get_token(email, password, scope: SCOPE)
    {
      "url" => url,
      "client_id" => app.client_id,
      "client_secret" => app.client_secret,
      "email" => email,
      "access_token" => token.token
    }
  end
end

define_command(:toot) do
  |msg = read_from_minibuffer("Toot: ")|
  config = MedicineShield.get_config
  client = Mastodon::REST::Client.new(base_url: config["url"],
                                      bearer_token: config["access_token"])
  response = client.create_status(msg)
  message("Tooted")
end
