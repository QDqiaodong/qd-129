import axios from 'axios'

const baseURL = '/api'

const request = axios.create({
  baseURL,
  timeout: 10000
})

request.interceptors.response.use(
  response => response.data,
  error => {
    console.error('Request error:', error)
    throw error
  }
)

export const readingAreaApi = {
  getAll: () => request.get('/reading-area'),
  getById: id => request.get(`/reading-area/${id}`),
  create: data => request.post('/reading-area', data),
  update: data => request.put('/reading-area', data),
  delete: id => request.delete(`/reading-area/${id}`)
}

export const deskChairApi = {
  getAll: () => request.get('/desk-chair'),
  getByAreaId: areaId => request.get(`/desk-chair/area/${areaId}`),
  getByTagId: tagId => request.get(`/desk-chair/tag/${tagId}`),
  getByTagIds: tagIds => request.get('/desk-chair/tags', { params: { tagIds } }),
  getById: id => request.get(`/desk-chair/${id}`),
  create: data => request.post('/desk-chair', data),
  update: data => request.put('/desk-chair', data),
  delete: id => request.delete(`/desk-chair/${id}`),
  bindTags: (id, tagIds) => request.post(`/desk-chair/${id}/tags`, tagIds),
  updateArea: (id, data) => request.post(`/desk-chair/${id}/update-area`, data),
  getDimensions: () => request.get('/desk-chair/dimensions')
}

export const tagApi = {
  getAll: () => request.get('/tag'),
  getById: id => request.get(`/tag/${id}`),
  create: data => request.post('/tag', data),
  update: data => request.put('/tag', data),
  delete: id => request.delete(`/tag/${id}`),
  deleteFromDeskChair: (deskChairId, tagId) => request.delete(`/tag/desk-chair/${deskChairId}/tag/${tagId}`)
}

export const changeLogApi = {
  getAll: () => request.get('/area-change-log'),
  getByDeskChairId: deskChairId => request.get(`/area-change-log/desk-chair/${deskChairId}`)
}