from pathlib import Path
import shutil
import subprocess

root = Path(__file__).resolve().parent
base = root / "upstream"
out = root / "build"
if not (base / "Argus-frontend/package.json").exists():
    subprocess.run(["git", "submodule", "update", "--init"], cwd=root, check=True)
for name in ("Argus-frontend", "Argus-backend", "sql"):
    shutil.copytree(base / name, out / name, dirs_exist_ok=True,
                    ignore=shutil.ignore_patterns("node_modules", "dist", "target", ".env", "*.log"))
front = out / "Argus-frontend"
def edit(path, old, new):
    original = path.read_text("utf-8")
    if old not in original:
        raise RuntimeError(f"上游入口已变化：{path}")
    path.write_text(original.replace(old, new), "utf-8")

assets = front / "src/views/college"
assets.mkdir(exist_ok=True)
for file in (root / "workspace").glob("*"):
    if file.suffix in (".vue", ".ts"):
        shutil.copy(file, assets)
shutil.copy(next(root.glob("学生工作台预览/*/campus.jpg")), assets)
java = out / "Argus-backend/src/main/java/com/argus/rag/college"
(java / "mapper").mkdir(parents=True, exist_ok=True)
for file in (root / "workspace").rglob("*.java"):
    shutil.copy(file, java / file.relative_to(root / "workspace"))
shutil.copy(root / "workspace/college.sql", out / "sql")
router = front / "src/router/index.ts"
edit(router, "children: [", """children: [
        {path: 'college/:module?', name: 'college', component: () => import('../views/college/CollegeView.vue'), meta: {title: '我的大学 · Argus'}},
""")
edit(router, "redirect: '/app/groups'", "redirect: '/app/college'")
auth = front / "src/stores/auth.ts"
edit(auth,"USER_HOME_PATH = '/app/groups'", "USER_HOME_PATH = '/app/college'")
edit(auth,"BUSINESS_PATH_PREFIXES = [","BUSINESS_PATH_PREFIXES = ['/app/college', ")
layout = front / "src/layouts/DefaultLayout.vue"
edit(layout,"const allMenuItems = [","""const allMenuItems = [
  ...[['','我的大学'],['learn','学习助手'],['practice','专业实践'],['opps','校园机会'],['project','我的项目'],['career','升学就业'],['campus','校园办事']].map(([key,label]) => ({
    key: 'college-' + key, label, icon: '◇', path: '/app/college' + (key ? '/' + key : ''), roles: ['USER'] as const
  })),
""")
edit(layout,"const path = route.path","const path = route.path\n  if(path.startsWith('/app/college')) return 'college-' + (route.params.module || '')")
print(out)
edit(router,"../views/HomeView.vue","../views/college/Landing.vue")
edit(router,"title: '数据洞察中心'","title: 'Argus · 我的大学'")
edit(layout, "const sidebarCollapsed = ref(false)", "const sidebarCollapsed = ref(window.matchMedia('(max-width: 768px)').matches)")
edit(layout, "  .main-area {\n    margin-left: 0;", "  .main-area, .collapsed .main-area {\n    min-width: 0; margin-left: 0;")
test_dir = out / "Argus-backend/src/test/java/com/argus/rag/college"
test_dir.mkdir(parents=True, exist_ok=True)
for test in (root / "tests/java").glob("*.java"):
    shutil.copy(test, test_dir)
schema = out / "sql/schema.sql"
edit(schema, "VECTOR(512)", "VECTOR(1024)")
edit(schema, "（512 维）", "（1024 维）")
