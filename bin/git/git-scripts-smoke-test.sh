#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

tmp_root="$(mktemp -d)"
test_repo="${tmp_root}/demo-repo"
remote_repo="${tmp_root}/demo-remote.git"

cleanup() {
  rm -rf "$tmp_root"
}
trap cleanup EXIT

mkdir -p "$test_repo/bin/git/lib"
git init --bare "$remote_repo" >/dev/null
git init "$test_repo" >/dev/null

cp "$PROJECT_ROOT/bin/git/git-stage.sh" "$test_repo/bin/git/"
cp "$PROJECT_ROOT/bin/git/git-prepare-commit.sh" "$test_repo/bin/git/"
cp "$PROJECT_ROOT/bin/git/git-workflow-auto.sh" "$test_repo/bin/git/"
cp "$PROJECT_ROOT/bin/git/git-draft-commit-message.sh" "$test_repo/bin/git/"
cp "$PROJECT_ROOT/bin/git/git-commit-staged.sh" "$test_repo/bin/git/"
cp "$PROJECT_ROOT/bin/git/git-push-safe.sh" "$test_repo/bin/git/"
cp "$PROJECT_ROOT/bin/git/lib/git-common.sh" "$test_repo/bin/git/lib/"

chmod +x "$test_repo/bin/git/"*.sh "$test_repo/bin/git/lib/"*.sh

(
  cd "$test_repo"
  git config user.name "Smoke Test"
  git config user.email "smoke@example.com"
  git remote add origin "$remote_repo"

  printf 'hello\n' > README.md
  git add README.md
  git commit -m "chore(repo): bootstrap test repo" >/dev/null

  printf 'updated\n' > README.md
  ./bin/git/git-stage.sh README.md >/dev/null
  ./bin/git/git-draft-commit-message.sh --scope repo --type chore --subject "verify reusable git scripts" >/dev/null
  ./bin/git/git-commit-staged.sh --from-draft >/dev/null
  ./bin/git/git-push-safe.sh --skip-hook --before-push-cmd "git status --short >/dev/null" --remote origin --branch "$(git rev-parse --abbrev-ref HEAD)" >/dev/null

  cat <<'EOF' > ./bin/git/git-check.sh
#!/usr/bin/env bash
set -euo pipefail
exit 0
EOF
  chmod +x ./bin/git/git-check.sh

  printf 'workflow\n' > README.md
  ./bin/git/git-workflow-auto.sh --auto-push README.md >/dev/null
)

echo "git scripts smoke test passed"
