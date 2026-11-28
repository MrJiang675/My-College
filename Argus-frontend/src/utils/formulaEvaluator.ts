/** Restricted grammar only: no JavaScript execution, properties, assignments or implicit calls. */
export function compileFormula(expression: string, variables: string[]) {
  if (expression.length > 500) throw Error('表达式过长')
  const tokens =
    expression.match(/(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?|[A-Za-z_][A-Za-z_0-9]*|[^\s]/g) || []
  if (tokens.length > 256) throw Error('表达式过于复杂')
  const functions: Record<string, [number, number, (...v: number[]) => number]> = {
    sin: [1, 1, Math.sin],
    cos: [1, 1, Math.cos],
    tan: [1, 1, Math.tan],
    asin: [1, 1, Math.asin],
    acos: [1, 1, Math.acos],
    atan: [1, 1, Math.atan],
    exp: [1, 1, Math.exp],
    log: [1, 1, Math.log],
    ln: [1, 1, Math.log],
    sqrt: [1, 1, Math.sqrt],
    abs: [1, 1, Math.abs],
    pow: [2, 2, Math.pow],
    min: [2, 8, Math.min],
    max: [2, 8, Math.max],
  }
  type Fn = (values: Record<string, number>) => number
  let i = 0,
    depth = 0
  function primary(): Fn {
    if (++depth > 32) throw Error('括号嵌套过深')
    const t = tokens[i++]
    let result: Fn
    if (t === '(') {
      result = add()
      if (tokens[i++] !== ')') throw Error('缺少右括号')
    } else if (t && /^(?:\d|\.)/.test(t)) {
      const n = Number(t)
      if (!Number.isFinite(n)) throw Error('无效数字')
      result = () => n
    } else if (t && Object.hasOwn(functions, t)) {
      if (tokens[i++] !== '(') throw Error('函数需要括号')
      const args: Fn[] = [add()]
      while (tokens[i] === ',') {
        i++
        args.push(add())
      }
      if (tokens[i++] !== ')') throw Error('函数括号错误')
      const [min, max, fn] = functions[t]!
      if (args.length < min || args.length > max) throw Error('函数参数数量错误')
      result = (v) => fn(...args.map((f) => f(v)))
    } else if (t === 'pi') result = () => Math.PI
    else if (t === 'e') result = () => Math.E
    else if (t && variables.includes(t)) result = (v) => v[t]!
    else throw Error('不允许的符号：' + t)
    depth--
    return result
  }
  function power(): Fn {
    const a = primary()
    if (tokens[i] === '^') {
      i++
      const b = unary()
      return (v) => a(v) ** b(v)
    }
    return a
  }
  function unary(): Fn {
    if (tokens[i] === '-' || tokens[i] === '+') {
      const op = tokens[i++],
        a = unary()
      return (v) => (op === '-' ? -a(v) : a(v))
    }
    return power()
  }
  function mul(): Fn {
    let a = unary()
    while (tokens[i] === '*' || tokens[i] === '/') {
      const op = tokens[i++],
        l = a,
        r = unary()
      a = (v) => (op === '*' ? l(v) * r(v) : l(v) / r(v))
    }
    return a
  }
  function add(): Fn {
    let a = mul()
    while (tokens[i] === '+' || tokens[i] === '-') {
      const op = tokens[i++],
        l = a,
        r = mul()
      a = (v) => (op === '+' ? l(v) + r(v) : l(v) - r(v))
    }
    return a
  }
  const fn = add()
  if (i !== tokens.length) throw Error('表达式存在多余符号')
  return fn
}
