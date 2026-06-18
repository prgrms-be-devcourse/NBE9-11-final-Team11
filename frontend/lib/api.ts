const BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080"

export async function apiRequest<T>(
  method: string,
  path: string,
  body?: any
): Promise<T> {
  const url = `${BASE_URL}${path}`

  // HTTP specifications (RFC 7231) recommend against sending bodies in GET requests.
  if (method === "GET" && body) {
    throw new Error("GET requests should not have a body")
  }

  const options: RequestInit = {
    method,
    headers: {
      "Content-Type": "application/json",
    },
    credentials: "include",
  }

  if (body) {
    options.body = JSON.stringify(body)
  }

  const res = await fetch(url, options)

  if (!res.ok) {
    let errorData
    try {
      errorData = await res.json()
    } catch (e) {
      throw new Error(res.statusText || `Request failed with status ${res.status}`)
    }
    throw errorData
  }

  // Handle empty or void responses (e.g. logout)
  if (res.status === 204 || res.headers.get("Content-Length") === "0") {
    return {} as T
  }

  try {
    return await res.json()
  } catch (e) {
    return {} as T
  }
}
