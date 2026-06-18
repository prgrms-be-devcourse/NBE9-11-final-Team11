const BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080"

export async function apiRequest<T>(
  method: string,
  path: string,
  body?: any
): Promise<T> {
  const url = `${BASE_URL}${path}`

  // For GET with a body, use XMLHttpRequest because standard browser fetch throws TypeError
  if (method === "GET" && body) {
    return new Promise<T>((resolve, reject) => {
      const xhr = new XMLHttpRequest()
      xhr.open("GET", url, true)
      xhr.withCredentials = true
      xhr.setRequestHeader("Content-Type", "application/json")
      
      xhr.onload = () => {
        if (xhr.status >= 200 && xhr.status < 300) {
          try {
            resolve(JSON.parse(xhr.responseText) as T)
          } catch (e) {
            resolve(xhr.responseText as any)
          }
        } else {
          try {
            reject(JSON.parse(xhr.responseText))
          } catch (e) {
            reject(new Error(xhr.statusText || `Request failed with status ${xhr.status}`))
          }
        }
      }
      
      xhr.onerror = () => {
        reject(new Error("Network error"))
      }
      
      xhr.send(JSON.stringify(body))
    })
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
