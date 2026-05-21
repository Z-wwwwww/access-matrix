/**
 * 扁平数组 → 树结构（按 id/pid 关系构建）
 */
export function toTreeData(list, idField = 'id', pidField = 'pid') {
  const map = {}
  const roots = []

  list.forEach((item) => {
    map[item[idField]] = { ...item, children: [] }
  })

  list.forEach((item) => {
    const node = map[item[idField]]
    const parentId = item[pidField]
    if (parentId && parentId !== '0' && parentId !== 0 && map[parentId]) {
      map[parentId].children.push(node)
    } else {
      roots.push(node)
    }
  })

  // 去掉空 children
  function cleanEmpty(nodes) {
    nodes.forEach((n) => {
      if (n.children.length === 0) {
        delete n.children
      } else {
        cleanEmpty(n.children)
      }
    })
  }
  cleanEmpty(roots)

  return roots
}

/**
 * 树结构扁平化（用于搜索/过滤）
 */
export function flattenTree(tree, childrenField = 'children') {
  const result = []
  function walk(nodes) {
    nodes.forEach((node) => {
      result.push(node)
      if (node[childrenField]) {
        walk(node[childrenField])
      }
    })
  }
  walk(tree)
  return result
}

/**
 * 检查节点是否有子节点
 */
export function hasChildren(node) {
  return node.children && node.children.length > 0
}
