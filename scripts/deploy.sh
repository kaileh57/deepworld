#!/bin/sh
set -e
cd "$(dirname "$0")/.."

server_dir="${DEEPWORLD_SERVER_DIR:-$HOME/deepworld-server}"
minecraft_version=$(grep '^minecraft_version=' gradle.properties | cut -d= -f2)
loader_version=$(grep '^loader_version=' gradle.properties | cut -d= -f2)
fabric_api_version=$(grep '^fabric_api_version=' gradle.properties | cut -d= -f2)
installer_version=1.1.2

./gradlew build --console=plain -q
mkdir -p "$server_dir/mods"

launcher="$server_dir/fabric-server-launch.jar"
launcher_tag="$server_dir/.launcher_version"
if [ ! -f "$launcher" ] || [ "$(cat "$launcher_tag" 2>/dev/null)" != "$minecraft_version/$loader_version" ]; then
	curl -fsSL -o "$launcher" "https://meta.fabricmc.net/v2/versions/loader/$minecraft_version/$loader_version/$installer_version/server/jar"
	echo "$minecraft_version/$loader_version" > "$launcher_tag"
fi

fabric_api_jar="$server_dir/mods/fabric-api-$fabric_api_version.jar"
if [ ! -f "$fabric_api_jar" ]; then
	rm -f "$server_dir"/mods/fabric-api-*.jar
	encoded_version=$(printf '%s' "$fabric_api_version" | sed 's/+/%2B/g')
	curl -fsSL -o "$fabric_api_jar" "https://cdn.modrinth.com/data/P7dR8mSH/versions/$(curl -fsSL "https://api.modrinth.com/v2/project/fabric-api/version/$encoded_version" | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])')/fabric-api-$encoded_version.jar"
fi

rm -f "$server_dir"/mods/deepworld-*.jar
cp build/libs/deepworld-*.jar "$server_dir/mods/"
rm -f "$server_dir"/mods/deepworld-*-sources.jar

[ -f "$server_dir/eula.txt" ] || echo "eula=true" > "$server_dir/eula.txt"

mkdir -p "$server_dir/config/polymer"
if [ ! -f "$server_dir/config/polymer/auto-host.json" ]; then
	cat > "$server_dir/config/polymer/auto-host.json" <<'HOST'
{
	"enabled": true,
	"required": true,
	"type": "polymer:automatic",
	"message": "deepworld needs a resource pack for its textures. it downloads automatically.",
	"dialog_title": "the resource pack is still building",
	"dialog_default_body": "waiting...",
	"dialog_body_header": "this takes a moment on first start."
}
HOST
fi

if [ ! -f "$server_dir/start.sh" ]; then
	cat > "$server_dir/start.sh" <<'START'
#!/bin/sh
cd "$(dirname "$0")"
exec java -Xms4G -Xmx8G -jar fabric-server-launch.jar nogui
START
	chmod +x "$server_dir/start.sh"
fi

echo "deployed to $server_dir"
echo "start with $server_dir/start.sh, or restart it if it is running"
